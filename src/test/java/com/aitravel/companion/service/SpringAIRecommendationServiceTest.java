package com.aitravel.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitravel.companion.config.TravelAiProperties;
import com.aitravel.companion.exception.AiRecommendationException;
import com.aitravel.companion.model.Attraction;
import com.aitravel.companion.model.Interest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAIRecommendationServiceTest {

    private static final TravelerPreferences PREFERENCES = new TravelerPreferences(
            Set.of(Interest.HISTORY, Interest.ART), new BigDecimal("50.00"), "EUR", 180);

    private final ChatModel chatModel = mock(ChatModel.class);

    private SpringAIRecommendationService service(int maxCandidates, int maxRecommendations) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return new SpringAIRecommendationService(chatClient,
                new TravelAiProperties(true, "gpt-4o-mini", maxCandidates, maxRecommendations));
    }

    private void modelReplies(String json) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }

    @Test
    void returnsValidSelectionsWithReasons() {
        modelReplies("""
                {"selections":[
                  {"attractionId":"colosseum","reason":"Matches your HISTORY interest."},
                  {"attractionId":"pantheon","reason":"Close by and within budget."}]}
                """);

        List<AiRecommendation> result = service(10, 3).recommend(PREFERENCES, candidates());

        assertThat(result).containsExactly(
                new AiRecommendation("colosseum", "Matches your HISTORY interest."),
                new AiRecommendation("pantheon", "Close by and within budget."));
    }

    @Test
    void dropsIdsThatWereNotSupplied() {
        modelReplies("""
                {"selections":[
                  {"attractionId":"invented-place","reason":"Looks lovely."},
                  {"attractionId":"pantheon","reason":"Good fit."}]}
                """);

        assertThat(service(10, 3).recommend(PREFERENCES, candidates()))
                .extracting(AiRecommendation::attractionId).containsExactly("pantheon");
    }

    @Test
    void dropsDuplicatesAndBlankReasons() {
        modelReplies("""
                {"selections":[
                  {"attractionId":"pantheon","reason":"First."},
                  {"attractionId":"pantheon","reason":"Duplicate."},
                  {"attractionId":"colosseum","reason":"   "}]}
                """);

        assertThat(service(10, 3).recommend(PREFERENCES, candidates()))
                .containsExactly(new AiRecommendation("pantheon", "First."));
    }

    @Test
    void capsSelectionsAtMaxRecommendations() {
        modelReplies("""
                {"selections":[
                  {"attractionId":"colosseum","reason":"A."},
                  {"attractionId":"pantheon","reason":"B."},
                  {"attractionId":"trevi","reason":"C."}]}
                """);

        assertThat(service(10, 2).recommend(PREFERENCES, candidates())).hasSize(2);
    }

    @Test
    void sendsOnlyTheConfiguredNumberOfCandidatesToTheModel() {
        modelReplies("{\"selections\":[]}");

        service(2, 3).recommend(PREFERENCES, candidates());

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String text = prompt.getValue().toString();
        assertThat(text).contains("id: colosseum").contains("id: pantheon");
        assertThat(text).doesNotContain("id: trevi");
    }

    @Test
    void returnsEmptyWithoutCallingModelWhenNoCandidates() {
        assertThat(service(10, 3).recommend(PREFERENCES, List.of())).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void returnsEmptyWhenModelReturnsNoSelections() {
        modelReplies("{\"selections\":null}");

        assertThat(service(10, 3).recommend(PREFERENCES, candidates())).isEmpty();
    }

    @Test
    void wrapsModelFailuresWithoutExposingPrompt() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream 401"));

        assertThatThrownBy(() -> service(10, 3).recommend(PREFERENCES, candidates()))
                .isInstanceOf(AiRecommendationException.class)
                .hasMessage("AI recommendation request failed");
    }

    private static List<Attraction> candidates() {
        return List.of(
                new Attraction("colosseum", "Colosseum", Set.of(Interest.HISTORY),
                        new BigDecimal("41.8902"), new BigDecimal("12.4922"),
                        new BigDecimal("18.00"), "EUR", 90, "Ancient amphitheatre"),
                new Attraction("pantheon", "Pantheon", Set.of(Interest.HISTORY, Interest.ART),
                        new BigDecimal("41.8986"), new BigDecimal("12.4769"),
                        null, null, 45, null),
                new Attraction("trevi", "Trevi Fountain", Set.of(Interest.ART),
                        new BigDecimal("41.9009"), new BigDecimal("12.4833"),
                        null, null, 30, null));
    }
}
