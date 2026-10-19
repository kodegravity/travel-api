package com.aitravel.session.messaging;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Messaging beans. The outbox always exists (doing nothing while {@code travel.messaging.enabled=false}); the SNS
 * publisher (a plain SDK client) and the SQS consumer (Spring Cloud AWS) exist only when messaging is on.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfig {

    @Bean
    @ConditionalOnMissingBean
    ProcessedEventStore inMemoryProcessedEventStore() {
        return new InMemoryProcessedEventStore();
    }

    /** Credentials and region from the AWS default chain (the ECS task role and AWS_REGION), as for DynamoDB. */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "travel.messaging", name = "enabled", havingValue = "true")
    SnsClient travelEventsSnsClient(MessagingProperties properties) {
        SnsClientBuilder builder = SnsClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(Duration.ofSeconds(3)));
        if (!properties.snsEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.snsEndpoint()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "travel.messaging", name = "enabled", havingValue = "true")
    EventPublisher snsEventPublisher(SnsClient travelEventsSnsClient, MessagingProperties properties) {
        return new SnsEventPublisher(travelEventsSnsClient, properties.topicArn());
    }

    @Bean
    EventOutbox eventOutbox(MessagingProperties properties, JsonMapper mapper, Clock clock,
                            ObjectProvider<OutboxStore> store, ObjectProvider<EventPublisher> publisher,
                            MeterRegistry meters) {
        return new EventOutbox(properties, mapper, clock, store.getIfAvailable(), publisher.getIfAvailable(), meters);
    }

    @Bean
    @ConditionalOnProperty(prefix = "travel.messaging", name = "enabled", havingValue = "true")
    NotificationEventListener notificationEventListener(JsonMapper mapper, ProcessedEventStore processed,
                                                        MeterRegistry meters) {
        return new NotificationEventListener(mapper, processed, meters);
    }

    /**
     * The factory every {@code @SqsListener} uses: containers do not start with the context (see
     * {@link SqsListenerStarter}), and a missing queue is an error, never created here (queues come from Terraform).
     */
    @Bean
    @ConditionalOnProperty(prefix = "travel.messaging", name = "enabled", havingValue = "true")
    SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options.autoStartup(false).queueNotFoundStrategy(QueueNotFoundStrategy.FAIL))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "travel.messaging", name = "enabled", havingValue = "true")
    SqsListenerStarter sqsListenerStarter(MessageListenerContainerRegistry registry) {
        return new SqsListenerStarter(registry, Duration.ofMinutes(1));
    }
}
