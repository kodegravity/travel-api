package com.aitravel.session.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Messaging beans. The outbox always exists (doing nothing while {@code travel.messaging.enabled=false}); the SNS
 * publisher and the SQS consumer exist only when messaging is on, as do the Spring Cloud AWS clients.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfig {

    @Bean
    @ConditionalOnMissingBean
    ProcessedEventStore inMemoryProcessedEventStore() {
        return new InMemoryProcessedEventStore();
    }

    @Bean
    @ConditionalOnProperty(prefix = "travel.messaging", name = "enabled", havingValue = "true")
    EventPublisher snsEventPublisher(SnsClient snsClient, MessagingProperties properties) {
        return new SnsEventPublisher(snsClient, properties.topicArn());
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
}
