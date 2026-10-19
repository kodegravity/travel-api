package com.aitravel.recommendation.live;

import static com.aitravel.recommendation.live.LiveFixtures.GAME;
import static com.aitravel.recommendation.live.LiveFixtures.JAZZ;
import static com.aitravel.recommendation.live.LiveFixtures.NOW;
import static com.aitravel.recommendation.live.LiveFixtures.PLAY;
import static com.aitravel.recommendation.live.LiveFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitravel.recommendation.config.TravelAiProperties;
import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.live.LiveEvent.EventType;
import com.aitravel.recommendation.model.Interest;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class OpenAiLiveEventWriterTest {

    private static final List<LiveEvent> CANDIDATES = List.of(PLAY, JAZZ, GAME);
    private static final LiveSuggestionRequest REQUEST =
            request(Set.of(Interest.LOCAL_EXPERIENCES), EnumSet.allOf(EventType.class), Set.of());

    private final ChatModel chatModel = mock(ChatModel.class);

    private OpenAiLiveEventWriter writer(int maxCallsPerDay) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        return new OpenAiLiveEventWriter(ChatClient.builder(chatModel).build(),
                new TravelAiProperties(true, "gpt-4o-mini", 10, 3, 1000, Duration.ofSeconds(5)),
                new LiveSuggestionProperties(5, 6, 8, 2, maxCallsPerDay), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void modelReplies(String json) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }

    @Test
    void keepsGroundedSelectionsAndSendsEventFactsWithoutCoordinates() {
        modelReplies("""
                {"selections":[
                  {"eventId":"G1","headline":"Derby night at the Bernabéu","message":"A big local match, a short ride away."},
                  {"eventId":"C1","headline":"Jazz around the corner","message":"Live jazz at Café Central, a few minutes' walk."}
                ]}
                """);

        List<LiveSuggestion> suggestions = writer(10).write(REQUEST, CANDIDATES, 2);

        assertThat(suggestions).extracting(s -> s.event().id()).containsExactly("G1", "C1");
        assertThat(suggestions).allSatisfy(s -> assertThat(s.aiWritten()).isTrue());
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String text = prompt.getValue().getContents();
        assertThat(text).contains("Real Madrid vs Sevilla", "starts in about 300 minutes", "LOCAL_EXPERIENCES");
        assertThat(text).doesNotContain("40.4", "-3.7");
    }

    @Test
    void rejectsUnknownIdsDuplicatesInventedTimesAndPrices() {
        modelReplies("""
                {"selections":[
                  {"eventId":"NOPE","headline":"Made up","message":"Not a candidate."},
                  {"eventId":"G1","headline":"Kick-off 21:00","message":"Starts at the stadium."},
                  {"eventId":"C1","headline":"Jazz soon","message":"Starts in 20 minutes nearby."},
                  {"eventId":"A1","headline":"Theatre tonight","message":"Seats from €15 at Teatro Español."},
                  {"eventId":"C1","headline":"Jazz around the corner","message":"Live jazz at Café Central."},
                  {"eventId":"C1","headline":"Again","message":"Duplicate."}
                ]}
                """);

        assertThat(writer(10).write(REQUEST, CANDIDATES, 2)).singleElement()
                .satisfies(s -> assertThat(s.headline()).isEqualTo("Jazz around the corner"));
    }

    @Test
    void theDailyCapStopsCallsToTheModel() {
        assertThatThrownBy(() -> writer(0).write(REQUEST, CANDIDATES, 2))
                .isInstanceOf(AiRecommendationException.class)
                .hasMessageContaining("live-event limit");
        verify(chatModel, never()).call(any(Prompt.class));
    }
}
