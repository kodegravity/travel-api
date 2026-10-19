package com.aitravel.session.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aitravel.session.logging.RequestLoggingFilter;
import com.aitravel.session.messaging.events.TravelSessionEnded;
import com.aitravel.session.repository.InMemoryTravelSessionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EventOutboxTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final MessagingProperties ENABLED = new MessagingProperties(true, "arn:test", "q", Duration.ofSeconds(20), Duration.ofDays(7));

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final InMemoryTravelSessionRepository store = new InMemoryTravelSessionRepository();
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private EventOutbox outbox(Clock clock) {
        return new EventOutbox(ENABLED, mapper, clock, store, publisher, meters);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void buildsTheEnvelopeWithIdTypeVersionTimeCorrelationAndPayload() {
        MDC.put(RequestLoggingFilter.REQUEST_ID_MDC_KEY, "req-123");

        OutboxEntry entry = outbox(Clock.fixed(NOW, ZoneOffset.UTC))
                .prepare(EventType.TRAVEL_SESSION_ENDED, new TravelSessionEnded("s1", "STOPPED")).get(0);

        JsonNode json = mapper.readTree(entry.json());
        assertThat(json.get("eventId").asString()).isEqualTo(entry.eventId()).hasSize(36);
        assertThat(json.get("eventType").asString()).isEqualTo("TravelSessionEnded");
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(json.get("timestamp").asString()).isEqualTo("2026-10-19T12:00:00Z");
        assertThat(json.get("correlationId").asString()).isEqualTo("req-123");
        assertThat(json.get("payload").get("reason").asString()).isEqualTo("STOPPED");
    }

    @Test
    void publishesAfterCommitAndClearsTheOutbox() {
        EventOutbox outbox = outbox(Clock.fixed(NOW, ZoneOffset.UTC));
        List<OutboxEntry> entries = outbox.prepare(EventType.TRAVEL_SESSION_ENDED, new TravelSessionEnded("s1", "STOPPED"));
        store.addOutbox(entries);

        outbox.afterCommit(entries);

        verify(publisher).publish(entries.get(0));
        assertThat(store.pendingOutbox(NOW.plusSeconds(60), 10)).isEmpty();
        assertThat(meters.counter("travel.events.published", "eventType", "TravelSessionEnded", "outcome", "success").count()).isEqualTo(1);
    }

    @Test
    void aFailedPublishStaysInTheOutboxUntilTheSweepSendsIt() {
        EventOutbox outbox = outbox(Clock.fixed(NOW, ZoneOffset.UTC));
        List<OutboxEntry> entries = outbox.prepare(EventType.TRAVEL_SESSION_ENDED, new TravelSessionEnded("s1", "STOPPED"));
        store.addOutbox(entries);
        doThrow(new RuntimeException("SNS down")).doNothing().when(publisher).publish(any());

        outbox.afterCommit(entries);
        assertThat(store.pendingOutbox(NOW.plusSeconds(60), 10)).hasSize(1);

        // The sweep only picks up entries older than relayMinAge.
        outbox(Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC)).sweep();

        verify(publisher, times(2)).publish(entries.get(0));
        assertThat(store.pendingOutbox(NOW.plusSeconds(60), 10)).isEmpty();
    }

    @Test
    void standaloneEventsAreStoredThenPublishedAndNeverThrow() {
        doThrow(new RuntimeException("SNS down")).when(publisher).publish(any());

        outbox(Clock.fixed(NOW, ZoneOffset.UTC)).publishStandalone(EventType.TRAVEL_SESSION_ENDED, new TravelSessionEnded("s1", "EXPIRED"));

        assertThat(store.pendingOutbox(NOW.plusSeconds(60), 10)).singleElement()
                .satisfies(entry -> assertThat(entry.eventType()).isEqualTo("TravelSessionEnded"));
    }

    @Test
    void disabledDoesNothingAtAll() {
        EventOutbox disabled = EventOutbox.disabled();

        assertThat(disabled.prepare(EventType.TRAVEL_SESSION_ENDED, new TravelSessionEnded("s1", "STOPPED"))).isEmpty();
        disabled.publishStandalone(EventType.TRAVEL_SESSION_ENDED, new TravelSessionEnded("s1", "STOPPED"));
        disabled.sweep();
        verifyNoInteractions(publisher);
        verify(publisher, never()).publish(any());
    }
}
