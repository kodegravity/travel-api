package com.aitravel.session.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NotificationEventListenerTest {

    private static final String EVENT = """
            {"eventId": "e-1", "eventType": "NearbyEventDiscovered", "eventVersion": 1,
             "timestamp": "2026-10-19T12:00:00Z", "correlationId": "req-1",
             "payload": {"sessionId": "s1", "events": [{"eventId": "G1", "name": "Derby"}]}}
            """;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final NotificationEventListener listener =
            new NotificationEventListener(JsonMapper.builder().build(), new InMemoryProcessedEventStore(), meters);

    private double count(String eventType, String outcome) {
        return meters.counter("travel.events.consumed", "consumer", "notification", "eventType", eventType, "outcome", outcome).count();
    }

    @Test
    void handlesAnEventOnceAndDropsRedeliveries() {
        listener.onMessage(EVENT);
        listener.onMessage(EVENT);

        assertThat(count("NearbyEventDiscovered", "success")).isEqualTo(1);
        assertThat(count("NearbyEventDiscovered", "duplicate")).isEqualTo(1);
    }

    @Test
    void unreadableMessagesThrowSoSqsRetriesThenDeadLetters() {
        assertThatThrownBy(() -> listener.onMessage("not json")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> listener.onMessage("{\"eventType\": \"TravelSessionEnded\"}"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> listener.onMessage(EVENT.replace("NearbyEventDiscovered", "SomethingElse").replace("e-1", "e-2")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownTypeIsNotMarkedSoItsRetriesStillFailIntoTheDeadLetterQueue() {
        String unknown = EVENT.replace("NearbyEventDiscovered", "SomethingElse");

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> listener.onMessage(unknown)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(count("SomethingElse", "duplicate")).isZero();
    }

    @Test
    void aFailedHandlingReleasesTheClaimSoTheRetryRuns() {
        ProcessedEventStore store = new InMemoryProcessedEventStore();
        store.markProcessed("notification", "e-9");
        store.release("notification", "e-9");

        assertThat(store.markProcessed("notification", "e-9")).isTrue();
    }
}
