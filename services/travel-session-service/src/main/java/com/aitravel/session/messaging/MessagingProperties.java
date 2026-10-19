package com.aitravel.session.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Event-driven messaging, bound from {@code travel.messaging.*}. Off by default: nothing is written to the outbox,
 * published, or consumed, and the REST API behaves exactly as without it.
 *
 * @param enabled            publish to SNS and consume from SQS
 * @param topicArn           the travel events SNS topic (from Terraform; never hardcoded)
 * @param notificationQueue  SQS queue name for the notification consumer
 * @param relayMinAge        entries younger than this are left to the fast path
 * @param processedRetention how long handled event ids are remembered for duplicate detection
 *
 * <p>The relay's sweep interval is {@code travel.messaging.relay-interval-ms}, read by its {@code @Scheduled}.
 */
@ConfigurationProperties(prefix = "travel.messaging")
public record MessagingProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String topicArn,
        @DefaultValue("roam-notification-events") String notificationQueue,
        @DefaultValue("20s") Duration relayMinAge,
        @DefaultValue("7d") Duration processedRetention
) {
}
