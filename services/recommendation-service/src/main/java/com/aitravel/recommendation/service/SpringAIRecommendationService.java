package com.aitravel.recommendation.service;

import com.aitravel.recommendation.config.TravelAiProperties;
import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "travel.ai", name = "enabled", havingValue = "true")
public class SpringAIRecommendationService implements AIRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(SpringAIRecommendationService.class);
    private static final String TIMER_NAME = "travel.ai.selection";
    /** Clock times ("18:30", "6 pm") and opening-status claims are never in the candidate data, so they are rejected. */
    private static final Pattern CLOCK_TIME =
            Pattern.compile("(?i)\\b\\d{1,2}(:\\d{2})\\s*(am|pm)?\\b|\\b\\d{1,2}\\s*(am|pm)\\b");
    private static final Pattern OPENING_CLAIM =
            Pattern.compile("(?i)\\b(open|closed|opens|closes)\\s+(now|today|until|at|from|on|late)\\b|\\bopening hours?\\b");
    /** Prices are never invented: a currency symbol or ISO code followed by a number is rejected. */
    private static final Pattern PRICE_CLAIM = Pattern.compile("(?:[€$£]|\\b[A-Z]{3}\\b)\\s?\\d");

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
    private final MeterRegistry meterRegistry;
    /** Model calls run here so a slow reply can be abandoned at the configured timeout. Bounded to 4 threads. */
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "ai-recommendation");
        thread.setDaemon(true);
        return thread;
    });

    public SpringAIRecommendationService(ChatClient chatClient, TravelAiProperties properties,
                                         DailyCallLimiter aiDailyCallLimiter, MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.dailyCallLimiter = aiDailyCallLimiter;
        this.meterRegistry = meterRegistry;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public List<AiRecommendation> recommend(TravelerPreferences preferences, List<Attraction> candidates) {
        long startedAt = System.nanoTime();
        List<Attraction> limited = candidates.stream().limit(properties.maxCandidates()).toList();
        if (limited.isEmpty()) {
            record("no_candidates", 0, 0, startedAt);
            return List.of();
        }

        // Checked last, so calls that would be skipped anyway do not use up the daily allowance.
        if (!dailyCallLimiter.tryAcquire()) {
            record("limit_reached", limited.size(), 0, startedAt);
            throw new AiRecommendationException(
                    "Daily OpenAI call limit of " + dailyCallLimiter.maxCallsPerDay() + " reached", null);
        }

        SelectionResponse response;
        Future<SelectionResponse> future = executor.submit(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(preferences, limited))
                .call()
                .entity(SelectionResponse.class));
        try {
            response = future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            record("timeout", limited.size(), 0, startedAt);
            throw new AiRecommendationException("AI recommendation timed out after " + properties.timeout(), null);
        } catch (ExecutionException ex) {
            record("provider_error", limited.size(), 0, startedAt);
            throw new AiRecommendationException("AI recommendation request failed", ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            record("provider_error", limited.size(), 0, startedAt);
            throw new AiRecommendationException("AI recommendation was interrupted", ex);
        }

        if (response == null || response.selections() == null || response.selections().isEmpty()) {
            record("no_selections", limited.size(), 0, startedAt);
            return List.of();
        }
        List<AiRecommendation> validated = validate(response, limited);
        if (validated.isEmpty()) {
            record("invalid_selections", limited.size(), 0, startedAt);
            return List.of();
        }
        record("success", limited.size(), validated.size(), startedAt);
        return validated;
    }

    /** Records latency by outcome and logs one line per call. The log carries counts only, never coordinates or prompts. */
    private void record(String outcome, int candidates, int selected, long startedAt) {
        long elapsedNanos = System.nanoTime() - startedAt;
        meterRegistry.timer(TIMER_NAME, "outcome", outcome).record(Duration.ofNanos(elapsedNanos));
        log.info("AI selection outcome={} latencyMs={} candidates={} selected={} model={}", outcome,
                elapsedNanos / 1_000_000, candidates, selected, properties.model());
    }

    /** Keeps only selections that reference a supplied candidate, are unique, have a reason, and fit the limit. */
    private List<AiRecommendation> validate(SelectionResponse response, List<Attraction> candidates) {
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
            if (inventsFacts(reason)) {
                log.warn("AI reason rejected for attraction {}: contains a clock time, opening status, or price", id);
                continue;
            }
            result.add(new AiRecommendation(id, reason));
        }
        return List.copyOf(result);
    }

    static boolean inventsFacts(String reason) {
        return CLOCK_TIME.matcher(reason).find()
                || OPENING_CLAIM.matcher(reason).find()
                || PRICE_CLAIM.matcher(reason).find();
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
