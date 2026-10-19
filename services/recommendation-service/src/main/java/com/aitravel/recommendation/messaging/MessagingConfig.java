package com.aitravel.recommendation.messaging;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import tools.jackson.databind.json.JsonMapper;

/** The travel events consumer, only with {@code travel.messaging.enabled=true}. */
@Configuration
@ConditionalOnProperty(prefix = "travel.messaging", name = "enabled", havingValue = "true")
public class MessagingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "travel.ai", name = "enabled", havingValue = "true")
    AiWarmup aiWarmup() {
        return new AiWarmup(Clock.systemUTC(), Duration.ofMinutes(30));
    }

    @Bean
    SessionEventListener sessionEventListener(JsonMapper mapper, ObjectProvider<AiWarmup> warmup, MeterRegistry meters) {
        return new SessionEventListener(mapper, warmup.getIfAvailable(), meters);
    }

    /**
     * The factory every {@code @SqsListener} uses: containers do not start with the context (see
     * {@link SqsListenerStarter}), and a missing queue is an error, never created here (queues come from Terraform).
     */
    @Bean
    SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options.autoStartup(false).queueNotFoundStrategy(QueueNotFoundStrategy.FAIL))
                .build();
    }

    @Bean
    SqsListenerStarter sqsListenerStarter(MessageListenerContainerRegistry registry) {
        return new SqsListenerStarter(registry, Duration.ofMinutes(1));
    }
}
