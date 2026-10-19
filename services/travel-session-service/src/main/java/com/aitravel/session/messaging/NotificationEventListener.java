package com.aitravel.session.messaging;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes notification-worthy events (RecommendationsReady, NearbyEventDiscovered, TravelSessionEnded). There is no
 * push channel yet, so it records what it would send; Web Push plugs in here. Each event is handled once, by
 * eventId: validated first, then claimed, and the claim is released if handling fails, so the SQS retry really
 * retries. Unreadable or unknown messages throw, so SQS retries them and then moves them to the dead-letter queue.
 */
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    static final String CONSUMER = "notification";

    private final JsonMapper mapper;
    private final ProcessedEventStore processed;
    private final MeterRegistry meters;

    public NotificationEventListener(JsonMapper mapper, ProcessedEventStore processed, MeterRegistry meters) {
        this.mapper = mapper;
        this.processed = processed;
        this.meters = meters;
    }

    @SqsListener("${travel.messaging.notification-queue}")
    public void onMessage(String body) {
        EventEnvelope envelope = mapper.readValue(body, EventEnvelope.class);
        if (envelope.eventId() == null || envelope.eventType() == null) {
            meters.counter("travel.events.consumed", "consumer", CONSUMER, "eventType", "unknown", "outcome", "invalid").increment();
            throw new IllegalArgumentException("Event without id or type");
        }
        EventType type = EventType.fromWireName(envelope.eventType());
        if (!processed.markProcessed(CONSUMER, envelope.eventId())) {
            log.info("Duplicate event ignored eventType={} eventId={} correlationId={}", envelope.eventType(),
                    envelope.eventId(), envelope.correlationId());
            meters.counter("travel.events.consumed", "consumer", CONSUMER, "eventType", envelope.eventType(), "outcome", "duplicate").increment();
            return;
        }
        try {
            handle(type, envelope);
        } catch (RuntimeException ex) {
            processed.release(CONSUMER, envelope.eventId());
            meters.counter("travel.events.consumed", "consumer", CONSUMER, "eventType", envelope.eventType(), "outcome", "failed").increment();
            throw ex;
        }
        meters.counter("travel.events.consumed", "consumer", CONSUMER, "eventType", envelope.eventType(), "outcome", "success").increment();
    }

    private void handle(EventType type, EventEnvelope envelope) {
        String sessionId = envelope.payload() == null || envelope.payload().get("sessionId") == null
                ? "unknown" : envelope.payload().get("sessionId").asString();
        switch (type) {
            case RECOMMENDATIONS_READY, NEARBY_EVENT_DISCOVERED ->
                    log.info("Notification skipped, no push channel configured eventType={} sessionId={} eventId={} correlationId={}",
                            envelope.eventType(), sessionId, envelope.eventId(), envelope.correlationId());
            case TRAVEL_SESSION_ENDED ->
                    log.info("Session ended, nothing to notify sessionId={} eventId={}", sessionId, envelope.eventId());
            default -> log.info("Event not handled by notifications eventType={}", envelope.eventType());
        }
    }
}
