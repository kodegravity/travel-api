package com.aitravel.session.messaging;

import com.aitravel.session.push.PushMessage;
import com.aitravel.session.push.PushNotifier;
import com.aitravel.session.repository.TravelSessionRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes notification-worthy events (RecommendationsReady, NearbyEventDiscovered, TravelSessionEnded) and sends
 * them to the session owner's browsers through Web Push. Push is best effort: a failed send is logged, never retried,
 * so a traveler is not notified twice. Each event is handled once, by
 * eventId: validated first, then claimed, and the claim is released if handling fails, so the SQS retry really
 * retries. Unreadable or unknown messages throw, so SQS retries them and then moves them to the dead-letter queue.
 */
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    static final String CONSUMER = "notification";

    private final JsonMapper mapper;
    private final ProcessedEventStore processed;
    private final MeterRegistry meters;
    private final TravelSessionRepository sessions;
    private final PushNotifier push;

    public NotificationEventListener(JsonMapper mapper, ProcessedEventStore processed, MeterRegistry meters) {
        this(mapper, processed, meters, null, PushNotifier.none());
    }

    /** {@code sessions} may be null only with {@link PushNotifier#none()}. */
    public NotificationEventListener(JsonMapper mapper, ProcessedEventStore processed, MeterRegistry meters,
                                     TravelSessionRepository sessions, PushNotifier push) {
        this.mapper = mapper;
        this.processed = processed;
        this.meters = meters;
        this.sessions = sessions;
        this.push = push;
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
            case RECOMMENDATIONS_READY -> {
                JsonNode placeIds = envelope.payload().get("placeIds");
                int count = placeIds != null && placeIds.isArray() ? placeIds.size() : 0;
                if (count > 0) {
                    // Same tag as the page's own notification, so the two replace each other.
                    push(sessionId, envelope, new PushMessage(count + " new " + (count == 1 ? "place" : "places")
                            + " nearby", "Open ROAM to see them.", "travel-session-updates", "/travel-mode"));
                }
            }
            case NEARBY_EVENT_DISCOVERED -> {
                JsonNode events = envelope.payload().get("events");
                if (events != null && events.isArray()) {
                    for (int i = 0; i < Math.min(events.size(), 2); i++) {
                        JsonNode event = events.get(i);
                        String venue = text(event, "venueName");
                        push(sessionId, envelope, new PushMessage(
                                text(event, "headline") == null ? "Live event nearby" : text(event, "headline"),
                                venue == null ? text(event, "name") : text(event, "name") + " at " + venue,
                                "live-event-" + text(event, "eventId"), "/travel-mode"));
                    }
                }
            }
            case TRAVEL_SESSION_ENDED ->
                    log.info("Session ended, nothing to notify sessionId={} eventId={}", sessionId, envelope.eventId());
            default -> log.info("Event not handled by notifications eventType={}", envelope.eventType());
        }
    }

    private void push(String sessionId, EventEnvelope envelope, PushMessage message) {
        Optional<String> owner = owner(sessionId);
        if (owner.isEmpty()) {
            log.info("Notification skipped, session not found eventType={} sessionId={} eventId={}",
                    envelope.eventType(), sessionId, envelope.eventId());
            return;
        }
        int delivered = push.notify(owner.get(), message);
        meters.counter("travel.push.sent", "eventType", envelope.eventType(), "outcome",
                delivered > 0 ? "delivered" : "none").increment();
    }

    private Optional<String> owner(String sessionId) {
        if (sessions == null) {
            return Optional.empty();
        }
        try {
            return sessions.findById(UUID.fromString(sessionId)).map(session -> session.ownerSubject());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
