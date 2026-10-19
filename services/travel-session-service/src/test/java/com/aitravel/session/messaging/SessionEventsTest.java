package com.aitravel.session.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aitravel.session.config.TravelSessionProperties;
import com.aitravel.session.dto.CreateTravelSessionRequest;
import com.aitravel.session.dto.LiveEventDto;
import com.aitravel.session.dto.LiveSuggestionDto;
import com.aitravel.session.dto.LocationRequest;
import com.aitravel.session.dto.RecommendationDto;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.model.Interest;
import com.aitravel.session.repository.InMemoryTravelSessionRepository;
import com.aitravel.session.service.RecommendationService;
import com.aitravel.session.service.TravelSessionService;
import com.aitravel.session.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The travel session flow publishes the right events, once, without coordinates, and never fails because of SNS. */
class SessionEventsTest {

    private static final LocationRequest HERE = new LocationRequest(new BigDecimal("40.4169"), new BigDecimal("-3.7035"));

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MutableClock clock = new MutableClock();
    private final RecommendationService recommendations = mock(RecommendationService.class);
    private final List<OutboxEntry> published = new ArrayList<>();
    private final EventPublisher publisher = published::add;
    private InMemoryTravelSessionRepository repository;
    private TravelSessionService service;

    @BeforeEach
    void setUp() {
        clock.reset();
        repository = new InMemoryTravelSessionRepository();
        service = service(publisher);
    }

    private TravelSessionService service(EventPublisher eventPublisher) {
        EventOutbox outbox = new EventOutbox(new MessagingProperties(true, "arn:test", "q", Duration.ofSeconds(20), Duration.ofDays(7)),
                mapper, clock, repository, eventPublisher, new SimpleMeterRegistry());
        return new TravelSessionService(repository, recommendations, new TravelSessionProperties(250, 5, 30, 120, 30, 100, 30),
                clock, outbox);
    }

    private UUID create() {
        return service.create(new CreateTravelSessionRequest(Set.of(Interest.ART), new BigDecimal("50"), "EUR", 180), "user-1").sessionId();
    }

    private List<String> types() {
        return published.stream().map(OutboxEntry::eventType).toList();
    }

    private JsonNode payload(int index) {
        return mapper.readTree(published.get(index).json()).get("payload");
    }

    @Test
    void startRefreshAndStopPublishTheirEvents() {
        UUID id = create();
        when(recommendations.recommend(any(RecommendationRequest.class), anySet())).thenReturn(new RecommendationResponse(List.of(
                new RecommendationDto("prado", "Prado", "ART", null, null, null, 120, null, null, null))));
        when(recommendations.liveSuggestions(any())).thenReturn(List.of(new LiveSuggestionDto(
                new LiveEventDto("G1", "Derby", LiveEventDto.EventType.SPORTS, null, "Bernabéu", 40.45, -3.69, null, null, null, 4.2, null, null, "ticketmaster"),
                "Derby night", "Big match nearby.", true)));

        service.refresh(id, "user-1", HERE, Set.of(LiveEventDto.EventType.SPORTS), Set.of());
        service.stop(id, "user-1");
        service.stop(id, "user-1");

        assertThat(types()).containsExactly("TravelSessionStarted", "RecommendationsReady", "NearbyEventDiscovered", "TravelSessionEnded");
        assertThat(payload(0).get("ownerRef").asString()).hasSize(12).doesNotContain("user-1");
        assertThat(payload(1).get("placeIds").get(0).asString()).isEqualTo("prado");
        assertThat(payload(2).get("events").get(0).get("eventId").asString()).isEqualTo("G1");
        assertThat(payload(3).get("reason").asString()).isEqualTo("STOPPED");
        // No event ever carries where the traveler is.
        assertThat(published).allSatisfy(entry -> assertThat(entry.json()).doesNotContain("40.4169", "-3.7035", "40.45"));
        assertThat(repository.pendingOutbox(clock.instant().plusSeconds(3600), 10)).isEmpty();
    }

    @Test
    void aRefreshWithNothingNewPublishesNothing() {
        UUID id = create();
        when(recommendations.recommend(any(RecommendationRequest.class), anySet())).thenReturn(new RecommendationResponse(List.of()));

        service.refresh(id, "user-1", HERE);

        assertThat(types()).containsExactly("TravelSessionStarted");
    }

    @Test
    void anSnsOutageNeverBreaksTheApiAndTheEventWaitsInTheOutbox() {
        EventPublisher down = entry -> {
            throw new IllegalStateException("SNS unavailable");
        };
        service = service(down);

        UUID id = create();

        assertThat(id).isNotNull();
        assertThat(repository.pendingOutbox(clock.instant().plusSeconds(60), 10)).singleElement()
                .satisfies(entry -> assertThat(entry.eventType()).isEqualTo("TravelSessionStarted"));
    }

    @Test
    void anExpiredSessionAnnouncesItsEndOnce() {
        UUID id = create();
        clock.advance(Duration.ofHours(3));

        for (int i = 0; i < 2; i++) {
            try {
                service.refresh(id, "user-1", HERE);
            } catch (RuntimeException expected) {
                // Expired sessions are rejected.
            }
        }

        assertThat(types()).containsExactly("TravelSessionStarted", "TravelSessionEnded");
        assertThat(payload(1).get("reason").asString()).isEqualTo("EXPIRED");
    }

    @Test
    void messagingOffMeansNoEvents() {
        service = new TravelSessionService(repository, recommendations, new TravelSessionProperties(250, 5, 30, 120, 30, 100, 30), clock);

        create();

        assertThat(published).isEmpty();
        assertThat(repository.pendingOutbox(clock.instant().plusSeconds(60), 10)).isEmpty();
        doThrow(new IllegalStateException()).when(recommendations).liveSuggestions(any());
    }
}
