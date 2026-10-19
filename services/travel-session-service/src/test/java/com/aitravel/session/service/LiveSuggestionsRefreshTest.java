package com.aitravel.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitravel.session.config.TravelSessionProperties;
import com.aitravel.session.dto.CreateTravelSessionRequest;
import com.aitravel.session.dto.LiveEventDto;
import com.aitravel.session.dto.LiveEventDto.EventType;
import com.aitravel.session.dto.LiveSuggestionDto;
import com.aitravel.session.dto.LiveSuggestionRequest;
import com.aitravel.session.dto.LocationRequest;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.dto.RefreshStatus;
import com.aitravel.session.dto.TravelSessionRecommendationResponse;
import com.aitravel.session.exception.AttractionProviderException;
import com.aitravel.session.model.Interest;
import com.aitravel.session.repository.InMemoryTravelSessionRepository;
import com.aitravel.session.support.MutableClock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LiveSuggestionsRefreshTest {

    private static final String OWNER = "user-owner";
    private static final LocationRequest HERE = new LocationRequest(new BigDecimal("40.4169"), new BigDecimal("-3.7035"));
    private static final LiveSuggestionDto GAME = new LiveSuggestionDto(
            new LiveEventDto("G1", "Real Madrid vs Sevilla", EventType.SPORTS, "Soccer", "Bernabéu", 40.45, -3.69,
                    null, "2026-11-14", "21:00:00", 4.2, "https://www.ticketmaster.es/event/G1", null, "ticketmaster"),
            "Derby night at the Bernabéu", "A big local match, a short ride away.", true);

    private final MutableClock clock = new MutableClock();
    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private TravelSessionService service;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        clock.reset();
        service = new TravelSessionService(new InMemoryTravelSessionRepository(), recommendationService,
                new TravelSessionProperties(250, 5, 30, 120, 30, 100, 30), clock);
        sessionId = service.create(new CreateTravelSessionRequest(Set.of(Interest.ART), new BigDecimal("50"), "EUR", 180),
                OWNER).sessionId();
        when(recommendationService.recommend(any(RecommendationRequest.class), anySet()))
                .thenReturn(new RecommendationResponse(List.of()));
    }

    @Test
    void passesInterestsTypesAndSeenEventsAndReturnsTheAlerts() {
        when(recommendationService.liveSuggestions(any())).thenReturn(List.of(GAME));

        TravelSessionRecommendationResponse response =
                service.refresh(sessionId, OWNER, HERE, Set.of(EventType.SPORTS, EventType.MUSIC), Set.of("C1"));

        assertThat(response.liveSuggestions()).containsExactly(GAME);
        assertThat(response.refreshStatus()).isEqualTo(RefreshStatus.NO_NEW_RESULTS);
        ArgumentCaptor<LiveSuggestionRequest> sent = ArgumentCaptor.forClass(LiveSuggestionRequest.class);
        verify(recommendationService).liveSuggestions(sent.capture());
        assertThat(sent.getValue().interests()).containsExactly(Interest.ART);
        assertThat(sent.getValue().eventTypes()).containsExactlyInAnyOrder(EventType.SPORTS, EventType.MUSIC);
        assertThat(sent.getValue().excludedEventIds()).containsExactly("C1");
        assertThat(sent.getValue().location()).isEqualTo(HERE);
    }

    @Test
    void alertsAreOffWithoutEventTypes() {
        TravelSessionRecommendationResponse response = service.refresh(sessionId, OWNER, HERE);

        assertThat(response.liveSuggestions()).isEmpty();
        verify(recommendationService, never()).liveSuggestions(any());
    }

    @Test
    void anEventSourceFailureNeverFailsTheRefresh() {
        when(recommendationService.liveSuggestions(any())).thenThrow(new AttractionProviderException("down", null));

        TravelSessionRecommendationResponse response =
                service.refresh(sessionId, OWNER, HERE, Set.of(EventType.SPORTS), Set.of());

        assertThat(response.liveSuggestions()).isEmpty();
        assertThat(response.refreshStatus()).isEqualTo(RefreshStatus.NO_NEW_RESULTS);
    }

    @Test
    void aSkippedRefreshLooksForNoEvents() {
        when(recommendationService.liveSuggestions(any())).thenReturn(List.of(GAME));
        service.refresh(sessionId, OWNER, HERE, Set.of(EventType.SPORTS), Set.of());

        // Same place, inside the cooldown: no movement means no new look.
        TravelSessionRecommendationResponse second =
                service.refresh(sessionId, OWNER, HERE, Set.of(EventType.SPORTS), Set.of("G1"));

        assertThat(second.refreshStatus()).isEqualTo(RefreshStatus.UNCHANGED);
        assertThat(second.liveSuggestions()).isEmpty();
        verify(recommendationService).liveSuggestions(any());
    }
}
