package com.aitravel.session.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aitravel.session.messaging.EventOutbox;
import com.aitravel.session.messaging.EventPublisher;
import com.aitravel.session.messaging.MessagingProperties;
import com.aitravel.session.messaging.OutboxEntry;
import com.aitravel.session.repository.InMemoryTravelSessionRepository;
import com.aitravel.session.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Saving a plan publishes ItineraryUpdated only for itineraries that changed, and never exposes the subject. */
class TravelPlanServiceTest {

    private static final Instant T1 = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-20T11:00:00Z");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MutableClock clock = new MutableClock();
    private final List<OutboxEntry> published = new ArrayList<>();
    private final EventPublisher publisher = published::add;
    private final TravelPlanService service = new TravelPlanService(new InMemoryTravelPlanStore(), mapper,
            new EventOutbox(new MessagingProperties(true, "arn:test", "q", Duration.ofSeconds(20), Duration.ofDays(7), ""),
                    mapper, clock, new InMemoryTravelSessionRepository(), publisher, new SimpleMeterRegistry()),
            clock);

    private TravelPlanEntry madrid(Instant at, String itineraryJson) {
        return new TravelPlanEntry(at, mapper.readTree("{\"trip\": [], \"itinerary\": " + itineraryJson + "}"));
    }

    private JsonNode payload(int index) {
        return mapper.readTree(published.get(index).json()).get("payload");
    }

    @Test
    void publishesTheItineraryShapeOnFirstSave() {
        service.save("alice@example.com", new SaveTravelPlanRequest(0L, Map.of(
                "city:madrid", madrid(T1, "{\"days\": [{\"stops\": [{}, {}]}, {\"stops\": [{}]}]}"),
                "favorites", new TravelPlanEntry(T1, mapper.readTree("[]")))));

        assertThat(published).extracting(OutboxEntry::eventType).containsExactly("ItineraryUpdated");
        assertThat(payload(0).get("cityId").asString()).isEqualTo("madrid");
        assertThat(payload(0).get("dayCount").asInt()).isEqualTo(2);
        assertThat(payload(0).get("stopCount").asInt()).isEqualTo(3);
        assertThat(published.get(0).json()).doesNotContain("alice");
    }

    @Test
    void doesNotRepublishAnUnchangedItinerary() {
        String itinerary = "{\"days\": [{\"stops\": [{}]}]}";
        service.save("alice", new SaveTravelPlanRequest(0L, Map.of("city:madrid", madrid(T1, itinerary))));
        service.save("alice", new SaveTravelPlanRequest(1L, Map.of("city:madrid", madrid(T2, itinerary))));

        assertThat(published).hasSize(1);
    }

    @Test
    void aConflictingSaveChangesNothing() {
        service.save("alice", new SaveTravelPlanRequest(0L, Map.of("city:madrid", madrid(T1, "{\"days\": []}"))));

        assertThatThrownBy(() -> service.save("alice",
                new SaveTravelPlanRequest(0L, Map.of("city:rome", madrid(T2, "{\"days\": []}")))))
                .isInstanceOf(TravelPlanException.class)
                .extracting(ex -> ((TravelPlanException) ex).reason())
                .isEqualTo(TravelPlanException.Reason.VERSION_CONFLICT);
        assertThat(service.get("alice").entries()).containsOnlyKeys("city:madrid");
    }
}
