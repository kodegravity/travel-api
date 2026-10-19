package com.aitravel.session.messaging;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

/**
 * Publishes to SNS with the event type as a message attribute, so each SQS subscription's filter policy picks only
 * the events its consumer handles.
 */
public class SnsEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsEventPublisher.class);
    static final String EVENT_TYPE_ATTRIBUTE = "eventType";

    private final SnsClient sns;
    private final String topicArn;

    public SnsEventPublisher(SnsClient sns, String topicArn) {
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalStateException("travel.messaging.enabled=true requires TRAVEL_MESSAGING_TOPIC_ARN");
        }
        this.sns = sns;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(OutboxEntry entry) {
        String messageId = sns.publish(r -> r.topicArn(topicArn)
                .message(entry.json())
                .messageAttributes(Map.of(EVENT_TYPE_ATTRIBUTE, MessageAttributeValue.builder()
                        .dataType("String").stringValue(entry.eventType()).build())))
                .messageId();
        log.info("Event published eventType={} eventId={} snsMessageId={}", entry.eventType(), entry.eventId(), messageId);
    }
}
