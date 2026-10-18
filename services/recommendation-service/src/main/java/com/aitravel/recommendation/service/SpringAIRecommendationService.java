package com.aitravel.recommendation.service;

import com.aitravel.recommendation.config.TravelAiProperties;
import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "travel.ai", name = "enabled", havingValue = "true")
public class SpringAIRecommendationService implements AIRecommendationService {

    private static final String SYSTEM_PROMPT = """
            You are a travel assistant that selects attractions for a traveler from a fixed candidate list.
            Rules:
            - Select only attractions whose id appears in the candidate list. Never invent places.
            - Select at most the number of recommendations requested.
            - Select only candidates that match at least one of the traveler's interests.
            - Costs and visit durations are estimates, not verified prices or opening hours. Say so when you mention them.
            - Do not claim that any attraction is open, available, or crowded right now.
            - Each reason is one or two sentences and must rely only on the data given for that candidate.
            Respond only with the requested JSON structure.
            """;

    private final ChatClient chatClient;
    private final TravelAiProperties properties;
    private final DailyCallLimiter dailyCallLimiter;

    public SpringAIRecommendationService(ChatClient chatClient, TravelAiProperties properties,
                                         DailyCallLimiter aiDailyCallLimiter) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.dailyCallLimiter = aiDailyCallLimiter;
    }

    @Override
    public List<AiRecommendation> recommend(TravelerPreferences preferences, List<Attraction> candidates) {
        List<Attraction> limited = candidates.stream().limit(properties.maxCandidates()).toList();
        if (limited.isEmpty()) {
            return List.of();
        }

        // Checked last, so calls that would be skipped anyway do not use up the daily allowance.
        if (!dailyCallLimiter.tryAcquire()) {
            throw new AiRecommendationException(
                    "Daily OpenAI call limit of " + dailyCallLimiter.maxCallsPerDay() + " reached", null);
        }
        SelectionResponse response;
        try {
            response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt(preferences, limited))
                    .call()
                    .entity(SelectionResponse.class);
        } catch (RuntimeException ex) {
            throw new AiRecommendationException("AI recommendation request failed", ex);
        }
        return validate(response, limited);
    }

    /** Keeps only selections that reference a supplied candidate, are unique, have a reason, and fit the limit. */
    private List<AiRecommendation> validate(SelectionResponse response, List<Attraction> candidates) {
        if (response == null || response.selections() == null) {
            return List.of();
        }
        Map<String, Attraction> byId = new LinkedHashMap<>();
        candidates.forEach(attraction -> byId.putIfAbsent(attraction.id(), attraction));

        Set<String> seen = new LinkedHashSet<>();
        List<AiRecommendation> result = new ArrayList<>();
        for (Selection selection : response.selections()) {
            if (result.size() >= properties.maxRecommendations()) {
                break;
            }
            if (selection == null || selection.attractionId() == null || selection.reason() == null) {
                continue;
            }
            String id = selection.attractionId().trim();
            String reason = selection.reason().trim();
            if (reason.isEmpty() || !byId.containsKey(id) || !seen.add(id)) {
                continue;
            }
            result.add(new AiRecommendation(id, reason));
        }
        return List.copyOf(result);
    }

    private String userPrompt(TravelerPreferences preferences, List<Attraction> candidates) {
        String interests = preferences.interests().stream()
                .sorted()
                .map(Interest::name)
                .collect(Collectors.joining(", "));
        String candidateLines = candidates.stream()
                .map(this::candidateLine)
                .collect(Collectors.joining("\n"));
        return """
                Traveler interests: %s
                Maximum budget: %s %s (compare costs only when the candidate's currency is %s)
                Available time: %d minutes
                Select up to %d attractions.

                Candidates:
                %s
                """.formatted(interests,
                preferences.maxBudget().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                preferences.currencyCode(), preferences.currencyCode(),
                preferences.availableMinutes(), properties.maxRecommendations(), candidateLines);
    }

    private String candidateLine(Attraction attraction) {
        String categories = attraction.categories().stream().sorted().map(Interest::name)
                .collect(Collectors.joining(", "));
        String duration = attraction.estimatedDurationMinutes() == null
                ? "unknown" : attraction.estimatedDurationMinutes() + " min (estimate)";
        String cost = attraction.estimatedCost() == null
                ? "unknown"
                : attraction.currencyCode() + " " + attraction.estimatedCost().setScale(2, RoundingMode.HALF_UP)
                + " (estimate)";
        return "- id: %s | name: %s | categories: %s | visit: %s | cost: %s | description: %s".formatted(
                attraction.id(), attraction.name(), categories, duration, cost,
                attraction.description() == null ? "none" : attraction.description());
    }

    /** Structured output requested from the model. */
    public record SelectionResponse(List<Selection> selections) {
    }

    /** One model-chosen candidate with its reason. */
    public record Selection(String attractionId, String reason) {
    }
}
