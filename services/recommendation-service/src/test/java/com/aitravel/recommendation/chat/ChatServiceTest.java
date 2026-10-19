package com.aitravel.recommendation.chat;

import static com.aitravel.recommendation.chat.ChatFixtures.FAR;
import static com.aitravel.recommendation.chat.ChatFixtures.MARKET;
import static com.aitravel.recommendation.chat.ChatFixtures.PRADO;
import static com.aitravel.recommendation.chat.ChatFixtures.PROPERTIES;
import static com.aitravel.recommendation.chat.ChatFixtures.RETIRO;
import static com.aitravel.recommendation.chat.ChatFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.exception.AttractionProviderException;
import com.aitravel.recommendation.model.Interest;
import com.aitravel.recommendation.provider.AttractionProvider;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class ChatServiceTest {

    private static final ChatResponse.Weather RAIN = new ChatResponse.Weather("Rain", 12.0, true, 80, true);

    private final AttractionProvider attractions = mock(AttractionProvider.class);
    private final WeatherClient weather = mock(WeatherClient.class);
    private final ChatAssistant ai = mock(ChatAssistant.class);

    @BeforeEach
    void setUp() {
        when(attractions.findNearby(any(), any())).thenReturn(List.of(RETIRO, PRADO, MARKET, FAR));
        when(weather.current(any(), any())).thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private ChatService service(ChatAssistant assistant) {
        ObjectProvider<ChatAssistant> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(assistant);
        return new ChatService(attractions, weather, PROPERTIES, provider, Clock.systemUTC());
    }

    @Test
    void theAiRepliesWithCardsForRealNearbyPlaces() {
        when(ai.reply(any(), anyList(), any())).thenReturn(new ChatAssistant.Draft("Try the Prado.",
                List.of(new ChatAssistant.Pick("prado", "Velázquez and Goya, ten minutes away."))));

        ChatResponse response = service(ai).reply(request("What now?", true, Set.of(Interest.ART)));

        assertThat(response.aiWritten()).isTrue();
        assertThat(response.reply()).isEqualTo("Try the Prado.");
        assertThat(response.suggestions()).singleElement().satisfies(s -> {
            assertThat(s.name()).isEqualTo("Prado Museum");
            assertThat(s.distanceKm()).isEqualTo(1.0);
            assertThat(s.reason()).contains("Goya");
        });
    }

    @Test
    void offersOnlyPlacesInRangeInterestMatchesFirstAndIndoorsWhenRainIsLikely() {
        when(weather.current(any(), any())).thenReturn(Optional.of(RAIN));
        when(ai.reply(any(), anyList(), any())).thenReturn(new ChatAssistant.Draft("Stay dry.", List.of()));

        ChatResponse response = service(ai).reply(request("What now?", true, Set.of(Interest.ART, Interest.NATURE)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatCandidate>> offered = ArgumentCaptor.forClass(List.class);
        verify(ai).reply(any(), offered.capture(), any());
        assertThat(offered.getValue()).extracting(c -> c.attraction().id()).containsExactly("prado", "retiro", "market");
        assertThat(response.weather()).isEqualTo(RAIN);
    }

    @Test
    void aConversationReusesTheAreaSearch() {
        when(ai.reply(any(), anyList(), any())).thenReturn(new ChatAssistant.Draft("Ok.", List.of()));
        ChatService service = service(ai);

        service.reply(request("What now?", true, Set.of()));
        service.reply(request("And after that?", true, Set.of()));

        verify(attractions, times(1)).findNearby(any(), any());
    }

    @Test
    void withoutTheAiItStillSuggestsNearbyPlaces() {
        when(weather.current(any(), any())).thenReturn(Optional.of(RAIN));
        when(ai.reply(any(), anyList(), any())).thenThrow(new AiRecommendationException("Daily OpenAI chat limit of 200 reached", null));

        ChatResponse response = service(ai).reply(request("What now?", true, Set.of(Interest.ART)));

        assertThat(response.aiWritten()).isFalse();
        assertThat(response.reply()).contains("isn't available").contains("Rain is likely");
        assertThat(response.suggestions()).extracting(ChatResponse.Suggestion::placeId).containsExactly("prado", "market", "retiro");
        assertThat(response.suggestions().get(0).reason()).contains("indoors");
    }

    @Test
    void withoutALocationThereAreNoPaidLookups() {
        when(ai.reply(any(), anyList(), any())).thenReturn(new ChatAssistant.Draft("Where are you?", List.of()));

        ChatResponse response = service(ai).reply(request("Ideas for tonight?", false, Set.of()));

        assertThat(response.reply()).isEqualTo("Where are you?");
        verify(attractions, never()).findNearby(any(), any());
        verify(weather, never()).current(any(), any());
    }

    @Test
    void aPlacesFailureIsNotCachedAndNeverFailsTheChat() {
        when(attractions.findNearby(any(), any()))
                .thenThrow(new AttractionProviderException("down", null))
                .thenReturn(List.of(PRADO));
        ChatService service = service(null);

        assertThat(service.reply(request("What now?", true, Set.of())).suggestions()).isEmpty();
        assertThat(service.reply(request("What now?", true, Set.of())).suggestions()).hasSize(1);
    }
}
