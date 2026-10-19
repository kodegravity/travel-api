package com.aitravel.session.messaging;

import com.aitravel.session.logging.RequestLoggingFilter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.json.JsonMapper;

/**
 * The transactional outbox. {@link #prepare} builds entries that the repository writes in the same DynamoDB
 * transaction as the session change; {@link #afterCommit} then publishes them at once and removes them; a sweep
 * publishes anything left behind (a crash or an SNS outage between the two). Publishing never fails a request:
 * the worst case is a delay until the next sweep. Disabled, it does nothing at all.
 */
public class EventOutbox {

    private static final Logger log = LoggerFactory.getLogger(EventOutbox.class);
    private static final int EVENT_VERSION = 1;
    private static final int SWEEP_BATCH = 50;

    private final boolean enabled;
    private final JsonMapper mapper;
    private final Clock clock;
    private final OutboxStore store;
    private final EventPublisher publisher;
    private final MeterRegistry meters;
    private final MessagingProperties properties;

    public EventOutbox(MessagingProperties properties, JsonMapper mapper, Clock clock, OutboxStore store,
                       EventPublisher publisher, MeterRegistry meters) {
        this.enabled = properties.enabled() && store != null && publisher != null;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.store = store;
        this.publisher = publisher;
        this.meters = meters;
    }

    /** An outbox that writes and publishes nothing, for when messaging is off. */
    public static EventOutbox disabled() {
        return new EventOutbox(new MessagingProperties(false, "", "", null, null, ""), null, Clock.systemUTC(), null, null, null);
    }

    public boolean enabled() {
        return enabled;
    }

    /** The entries to write with a session change; none when messaging is off. */
    public List<OutboxEntry> prepare(EventType type, Object payload) {
        if (!enabled) {
            return List.of();
        }
        Instant now = clock.instant();
        String correlationId = MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY);
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), type.wireName(), EVENT_VERSION, now,
                correlationId, mapper.valueToTree(payload));
        return List.of(new OutboxEntry(envelope.eventId(), type.wireName(), mapper.writeValueAsString(envelope), now));
    }

    /** Publishes entries whose session change is committed. Failures stay in the outbox for the sweep. */
    public void afterCommit(List<OutboxEntry> entries) {
        if (!enabled) {
            return;
        }
        for (OutboxEntry entry : entries) {
            publishAndRemove(entry);
        }
    }

    /** An event with no session change: stored, then published. Never throws. */
    public void publishStandalone(EventType type, Object payload) {
        List<OutboxEntry> entries = prepare(type, payload);
        if (entries.isEmpty()) {
            return;
        }
        try {
            store.addOutbox(entries);
        } catch (RuntimeException ex) {
            log.warn("Outbox write failed eventType={} ({}); event dropped", type.wireName(), ex.getClass().getSimpleName());
            meters.counter("travel.events.published", "eventType", type.wireName(), "outcome", "outbox_failed").increment();
            return;
        }
        afterCommit(entries);
    }

    @Scheduled(fixedDelayString = "${travel.messaging.relay-interval-ms:30000}",
            initialDelayString = "${travel.messaging.relay-interval-ms:30000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        try {
            List<OutboxEntry> pending = store.pendingOutbox(clock.instant().minus(properties.relayMinAge()), SWEEP_BATCH);
            if (!pending.isEmpty()) {
                log.info("Outbox sweep found {} unpublished events", pending.size());
                pending.forEach(this::publishAndRemove);
            }
        } catch (RuntimeException ex) {
            log.warn("Outbox sweep failed ({})", ex.getClass().getSimpleName());
        }
    }

    private void publishAndRemove(OutboxEntry entry) {
        try {
            publisher.publish(entry);
        } catch (RuntimeException ex) {
            log.warn("Event publish failed eventType={} eventId={} ({}); kept for retry", entry.eventType(), entry.eventId(),
                    ex.getClass().getSimpleName());
            meters.counter("travel.events.published", "eventType", entry.eventType(), "outcome", "failed").increment();
            return;
        }
        meters.counter("travel.events.published", "eventType", entry.eventType(), "outcome", "success").increment();
        try {
            store.removeOutbox(entry.eventId());
        } catch (RuntimeException ex) {
            // Published but not removed: the sweep publishes it again, and consumers drop the duplicate by eventId.
            log.warn("Outbox cleanup failed eventId={} ({})", entry.eventId(), ex.getClass().getSimpleName());
        }
    }
}
