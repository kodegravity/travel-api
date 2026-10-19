package com.aitravel.recommendation.live;

import static com.aitravel.recommendation.live.LiveFixtures.CIRCUS;
import static com.aitravel.recommendation.live.LiveFixtures.GAME;
import static com.aitravel.recommendation.live.LiveFixtures.JAZZ;
import static com.aitravel.recommendation.live.LiveFixtures.PLAY;
import static com.aitravel.recommendation.live.LiveFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.live.LiveEvent.EventType;
import com.aitravel.recommendation.model.Interest;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class LiveSuggestionServiceTest {

    private final EventsClient eventsClient = mock(EventsClient.class);
    private final LiveEventWriter writer = mock(LiveEventWriter.class);
    private final LiveSuggestionProperties properties = new LiveSuggestionProperties(5, 6, 8, 2, 100);

    @BeforeEach
    void setUp() {
        when(eventsClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(GAME, JAZZ, PLAY, CIRCUS));
    }

    @SuppressWarnings("unchecked")
    private LiveSuggestionService service(LiveEventWriter ai) {
        ObjectProvider<LiveEventWriter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ai);
        return new LiveSuggestionService(eventsClient, properties, provider);
    }

    @Test
    void withoutAiSuggestsTheSoonestWantedEventsWithPlainText() {
        List<LiveSuggestion> suggestions = service(null)
                .suggest(request(Set.of(Interest.HISTORY), EnumSet.of(EventType.SPORTS, EventType.MUSIC), Set.of()));

        assertThat(suggestions).extracting(s -> s.event().id()).containsExactly("C1", "G1");
        assertThat(suggestions.get(0).headline()).isEqualTo("Live music nearby: Jazz Night");
        assertThat(suggestions.get(0).message()).isEqualTo("Café Central, about 0.4 km from you.");
        assertThat(suggestions.get(1).headline()).isEqualTo("Game nearby: Real Madrid vs Sevilla");
        assertThat(suggestions).allSatisfy(s -> assertThat(s.aiWritten()).isFalse());
    }

    @Test
    void skipsEventsAlreadySuggestedAndRanksInterestMatchesFirst() {
        List<LiveSuggestion> suggestions = service(null).suggest(
                request(Set.of(Interest.ART), EnumSet.allOf(EventType.class), Set.of("A1")));

        // ART favours music and shows; the play was already suggested, so jazz leads, then the soonest others.
        assertThat(suggestions).extracting(s -> s.event().id()).containsExactly("C1", "G1");
    }

    @Test
    void noMatchingEventsMeansNoAiCall() {
        List<LiveSuggestion> suggestions = service(writer).suggest(
                request(Set.of(), EnumSet.of(EventType.OTHER), Set.of()));

        assertThat(suggestions).isEmpty();
        verify(writer, never()).write(any(), anyList(), anyInt());
    }

    @Test
    void usesTheAiWhenAvailableAndFallsBackWhenItFails() {
        LiveSuggestion written = new LiveSuggestion(GAME, "Derby night at the Bernabéu", "A big match close by.", true);
        when(writer.write(any(), anyList(), eq(2))).thenReturn(List.of(written))
                .thenThrow(new AiRecommendationException("Daily OpenAI live-event limit of 100 reached", null));
        LiveSuggestionService service = service(writer);
        var request = request(Set.of(Interest.LOCAL_EXPERIENCES), EnumSet.of(EventType.SPORTS), Set.of());

        assertThat(service.suggest(request)).containsExactly(written);
        assertThat(service.suggest(request)).singleElement()
                .satisfies(s -> {
                    assertThat(s.aiWritten()).isFalse();
                    assertThat(s.event().id()).isEqualTo("G1");
                });
    }

    @Test
    void anEventWithoutVenueOrDistanceStillReadsWell() {
        when(eventsClient.nearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(LiveFixtures.event("X", "Night Market", EventType.OTHER, null, null, null)));

        assertThat(service(null).suggest(request(Set.of(), EnumSet.of(EventType.OTHER), Set.of())))
                .singleElement()
                .satisfies(s -> assertThat(s.message()).isEqualTo("Close to where you are."));
    }
}
