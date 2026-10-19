package com.aitravel.session.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.session.config.JwtTestSupport;
import com.aitravel.session.messaging.events.RecommendationsReady;
import com.aitravel.session.messaging.events.TravelSessionStarted;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * End to end against LocalStack: SNS topic → filtered subscription → SQS queue (with a dead-letter queue) → the real
 * {@code @SqsListener}. Run with LocalStack listening at {@code LOCALSTACK_ENDPOINT}, for example:
 * {@code docker run --rm -p 4566:4566 localstack/localstack:3.8.1} then
 * {@code LOCALSTACK_ENDPOINT=http://localhost:4566 ./mvnw test -Dtest=MessagingLocalStackTest}.
 */
@EnabledIfEnvironmentVariable(named = "LOCALSTACK_ENDPOINT", matches = ".+")
@SpringBootTest
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
class MessagingLocalStackTest {

    private static final String SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    private static final String QUEUE = "roam-notification-events-" + SUFFIX;
    private static SnsClient sns;
    private static SqsClient sqs;
    private static String topicArn;
    private static String queueUrl;
    private static String dlqUrl;

    @Autowired
    private EventOutbox outbox;

    @Autowired
    private MeterRegistry meters;

    @BeforeAll
    static void createTopicQueuesAndSubscription() {
        var endpoint = URI.create(System.getenv("LOCALSTACK_ENDPOINT"));
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        sns = SnsClient.builder().endpointOverride(endpoint).region(Region.US_EAST_2).credentialsProvider(credentials).build();
        sqs = SqsClient.builder().endpointOverride(endpoint).region(Region.US_EAST_2).credentialsProvider(credentials).build();

        topicArn = sns.createTopic(r -> r.name("roam-travel-events-" + SUFFIX)).topicArn();
        dlqUrl = sqs.createQueue(r -> r.queueName(QUEUE + "-dlq")).queueUrl();
        String dlqArn = sqs.getQueueAttributes(r -> r.queueUrl(dlqUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
        queueUrl = sqs.createQueue(r -> r.queueName(QUEUE).attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "1",
                QueueAttributeName.REDRIVE_POLICY, "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"2\"}"))).queueUrl();
        String queueArn = sqs.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
        sns.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(queueArn).attributes(Map.of(
                "RawMessageDelivery", "true",
                "FilterPolicy", "{\"eventType\":[\"RecommendationsReady\",\"NearbyEventDiscovered\",\"TravelSessionEnded\"]}")));
    }

    @AfterAll
    static void cleanUp() {
        sqs.deleteQueue(r -> r.queueUrl(queueUrl));
        sqs.deleteQueue(r -> r.queueUrl(dlqUrl));
        sns.deleteTopic(r -> r.topicArn(topicArn));
    }

    @DynamicPropertySource
    static void messaging(DynamicPropertyRegistry registry) {
        registry.add("travel.messaging.enabled", () -> "true");
        registry.add("travel.messaging.topic-arn", () -> topicArn);
        registry.add("travel.messaging.notification-queue", () -> QUEUE);
        registry.add("spring.cloud.aws.endpoint", () -> System.getenv("LOCALSTACK_ENDPOINT"));
        registry.add("spring.cloud.aws.region.static", () -> "us-east-2");
        registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
    }

    private double consumed(String eventType, String outcome) {
        var counter = meters.find("travel.events.consumed")
                .tags("consumer", "notification", "eventType", eventType, "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private static void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met within " + timeout);
            }
            Thread.sleep(200);
        }
    }

    @Test
    void theFilterDeliversOnlyNotificationEventsAndEachIsHandledOnce() throws Exception {
        outbox.publishStandalone(EventType.TRAVEL_SESSION_STARTED,
                new TravelSessionStarted("s1", "ref", Set.of(), "EUR", 60));
        List<OutboxEntry> ready = outbox.prepare(EventType.RECOMMENDATIONS_READY,
                new RecommendationsReady("s1", List.of("prado"), "refresh"));
        outbox.afterCommit(ready);
        // The same event again, as SNS or the outbox relay may deliver it twice.
        outbox.afterCommit(ready);

        await(() -> consumed("RecommendationsReady", "success") == 1 && consumed("RecommendationsReady", "duplicate") == 1,
                Duration.ofSeconds(20));
        Thread.sleep(1500);
        assertThat(consumed("TravelSessionStarted", "success")).isZero();
    }

    @Test
    void anUnreadableMessageEndsUpInTheDeadLetterQueue() throws Exception {
        sqs.sendMessage(r -> r.queueUrl(queueUrl).messageBody("not an event"));

        await(() -> sqs.getQueueAttributes(r -> r.queueUrl(dlqUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                .attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES).equals("1"),
                Duration.ofSeconds(30));
    }
}
