package com.aitravel.recommendation.chat;

import static com.aitravel.recommendation.chat.ChatFixtures.MARKET;
import static com.aitravel.recommendation.chat.ChatFixtures.PRADO;
import static com.aitravel.recommendation.chat.ChatFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitravel.recommendation.config.TravelAiProperties;
import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.model.Interest;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

class OpenAiChatAssistantTest {

    private static final List<ChatCandidate> CANDIDATES = List.of(
            new ChatCandidate(PRADO, 1.0, ChatCandidate.Setting.INDOOR),
            new ChatCandidate(MARKET, 0.5, ChatCandidate.Setting.INDOOR));

    private final ChatModel chatModel = mock(ChatModel.class);

    private OpenAiChatAssistant assistant(int maxCallsPerDay) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        return new OpenAiChatAssistant(ChatClient.builder(chatModel).build(),
                new TravelAiProperties(true, "gpt-4o-mini", 10, 3, 1000, Duration.ofSeconds(5)),
                new ChatProperties(maxCallsPerDay, 15, 3, Duration.ofMinutes(15), "https://api.open-meteo.com"),
                Clock.systemUTC());
    }

    @Test
    void sendsContextWeatherAndPlacesButNoCoordinatesAndKeepsValidPicks() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"reply": "It's raining, so head indoors: the Prado is a short walk.",
                 "suggestions": [
                   {"placeId": "prado", "reason": "World-class art, a short walk away."},
                   {"placeId": "invented", "reason": "Not a real candidate."},
                   {"placeId": "market", "reason": "Tapas from €5 at the counters."}
                 ]}
                """)))));

        ChatAssistant.Draft draft = assistant(10).reply(request("Raining, ideas?", true, Set.of(Interest.ART)),
                CANDIDATES, Optional.of(new com.aitravel.recommendation.chat.ChatResponse.Weather("Rain", 12.0, true, 80, true)));

        assertThat(draft.reply()).startsWith("It's raining");
        assertThat(draft.picks()).extracting(ChatAssistant.Pick::placeId).containsExactly("prado");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
                .contains("Madrid", "2026-10-19T15:30", "EUR 40", "180 minutes", "Royal Palace", "Rain, 12°C",
                        "Prado Museum", "INDOOR", "Traveler: Raining, ideas?")
                .doesNotContain("40.4169", "-3.7035");
    }

    @Test
    void anEmptyReplyIsAFailure() {
        assertThatThrownBy(() -> OpenAiChatAssistant.validate(new OpenAiChatAssistant.Reply(" ", List.of()), CANDIDATES))
                .isInstanceOf(AiRecommendationException.class);
    }

    @Test
    void theDailyCapStopsCalls() {
        assertThatThrownBy(() -> assistant(0).reply(request("Hi", true, Set.of()), CANDIDATES, Optional.empty()))
                .isInstanceOf(AiRecommendationException.class)
                .hasMessageContaining("chat limit");
        verify(chatModel, never()).call(any(Prompt.class));
    }
}
