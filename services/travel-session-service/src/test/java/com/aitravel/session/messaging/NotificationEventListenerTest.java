package com.aitravel.session.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aitravel.session.model.Interest;
import com.aitravel.session.model.TravelSession;
import com.aitravel.session.push.PushMessage;
import com.aitravel.session.repository.InMemoryTravelSessionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

    @Test
    void pushesNewPlacesAndLiveEventsToTheSessionOwner() {
        InMemoryTravelSessionRepository sessions = new InMemoryTravelSessionRepository();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        sessions.addIfCapacity(new TravelSession(sessionId, "okta-owner", Set.of(Interest.FOOD), BigDecimal.TEN, "EUR",
                60, now, Duration.ofMinutes(30), Duration.ofHours(2)), 10, now, Duration.ofMinutes(30), List.of());
        List<String> owners = new ArrayList<>();
        List<PushMessage> sent = new ArrayList<>();
        NotificationEventListener pushing = new NotificationEventListener(JsonMapper.builder().build(),
                new InMemoryProcessedEventStore(), meters, sessions, (subject, message) -> {
                    owners.add(subject);
                    sent.add(message);
                    return 1;
                });

        pushing.onMessage("""
                {"eventId": "e-10", "eventType": "RecommendationsReady", "eventVersion": 1,
                 "payload": {"sessionId": "%s", "placeIds": ["a", "b"], "source": "refresh"}}
                """.formatted(sessionId));
        pushing.onMessage("""
                {"eventId": "e-11", "eventType": "NearbyEventDiscovered", "eventVersion": 1,
                 "payload": {"sessionId": "%s", "events": [{"eventId": "G1", "name": "Derby", "venueName": "Arena",
                             "headline": "Derby tonight"}]}}
                """.formatted(sessionId));
        pushing.onMessage(EVENT.replace("e-1", "e-12"));

        assertThat(owners).containsOnly("okta-owner");
        assertThat(sent).containsExactly(
                new PushMessage("2 new places nearby", "Open ROAM to see them.", "travel-session-updates", "/travel-mode"),
                new PushMessage("Derby tonight", "Derby at Arena", "live-event-G1", "/travel-mode"));
    }
}
