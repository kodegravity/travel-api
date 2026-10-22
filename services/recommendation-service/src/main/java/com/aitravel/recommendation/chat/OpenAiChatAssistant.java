package com.aitravel.recommendation.chat;

import com.aitravel.recommendation.config.TravelAiProperties;
import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.model.Interest;
import com.aitravel.recommendation.service.DailyCallLimiter;
import com.aitravel.recommendation.service.SharedStore;
import com.aitravel.recommendation.service.SpringAIRecommendationService;
import jakarta.annotation.PreDestroy;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * "Ask ROAM" on gpt-4o-mini. The model sees the traveler's context, the weather, and a fixed list of real nearby
 * places; it may only suggest places from that list, and suggestion reasons that state a time, an opening, or a price
 * are dropped. Capped per day, with a timeout; any failure raises {@link AiRecommendationException} and the caller
 * answers without AI. Prompts and coordinates are never logged.
 */
@Component
@ConditionalOnProperty(prefix = "travel.ai", name = "enabled", havingValue = "true")
public class OpenAiChatAssistant implements ChatAssistant {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatAssistant.class);
    private static final int MAX_REPLY = 1500;
    private static final int MAX_PICKS = 3;

    private static final String SYSTEM_PROMPT = """
            You are ROAM, a friendly local travel assistant helping a traveler decide what to do next.
            Rules:
            - Use the traveler's local time, interests, budget, available time, trip, and the weather given.
            - Suggest specific places only from the "Nearby places" list, by their exact name; never invent places.
              If the list is empty or nothing fits, say so and give general advice instead.
            - When rain is likely, prefer INDOOR places; on pleasant days, outdoor places are welcome.
            - Prefer closer places and ones that fit the available time and budget.
            - Costs and visit times are estimates. Never state opening hours, ticket prices, or that a place is open
              now; suggest checking before going.
            - Keep the reply under 120 words, warm and practical. Use short paragraphs, no markdown headings.
            - Pick at most 3 places to show as cards, each with a one-sentence reason grounded in its data.
            Respond only with the requested JSON structure.
            """;

    private final ChatClient chatClient;
    private final TravelAiProperties aiProperties;
    private final DailyCallLimiter dailyCallLimiter;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ai-chat");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public OpenAiChatAssistant(ChatClient chatClient, TravelAiProperties aiProperties, ChatProperties properties,
                               SharedStore shared) {
        this(chatClient, aiProperties, properties, Clock.systemUTC(), shared);
    }

    OpenAiChatAssistant(ChatClient chatClient, TravelAiProperties aiProperties, ChatProperties properties, Clock clock) {
        this(chatClient, aiProperties, properties, clock, SharedStore.NONE);
    }

    OpenAiChatAssistant(ChatClient chatClient, TravelAiProperties aiProperties, ChatProperties properties, Clock clock,
                        SharedStore shared) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.dailyCallLimiter = new DailyCallLimiter("openai-chat", properties.aiMaxCallsPerDay(), clock, shared);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public Draft reply(ChatRequest request, List<ChatCandidate> candidates, Optional<ChatResponse.Weather> weather) {
        if (!dailyCallLimiter.tryAcquire()) {
            log.info("AI chat outcome=limit_reached");
            throw new AiRecommendationException("Daily OpenAI chat limit of " + dailyCallLimiter.maxCallsPerDay() + " reached", null);
        }
        long startedAt = System.nanoTime();
        Future<Reply> future = executor.submit(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(request, candidates, weather))
                .call()
                .entity(Reply.class));
        Reply reply;
        try {
            reply = future.get(aiProperties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.info("AI chat outcome=timeout latencyMs={}", elapsedMs(startedAt));
            throw new AiRecommendationException("AI chat timed out after " + aiProperties.timeout(), null);
        } catch (ExecutionException ex) {
            log.info("AI chat outcome=provider_error latencyMs={}", elapsedMs(startedAt));
            throw new AiRecommendationException("AI chat request failed", ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new AiRecommendationException("AI chat was interrupted", ex);
        }
        Draft draft = validate(reply, candidates);
        log.info("AI chat outcome=success latencyMs={} candidates={} picks={}", elapsedMs(startedAt), candidates.size(),
                draft.picks().size());
        return draft;
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    static Draft validate(Reply reply, List<ChatCandidate> candidates) {
        if (reply == null || reply.reply() == null || reply.reply().isBlank()) {
            throw new AiRecommendationException("AI chat returned no reply", null);
        }
        String text = reply.reply().trim();
        if (text.length() > MAX_REPLY) {
            text = text.substring(0, MAX_REPLY).trim() + "…";
        }
        Map<String, ChatCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(c -> c.attraction().id(), Function.identity(), (a, b) -> a));
        Set<String> seen = new LinkedHashSet<>();
        List<Pick> picks = new ArrayList<>();
        for (Pick pick : reply.suggestions() == null ? List.<Pick>of() : reply.suggestions()) {
            if (picks.size() >= MAX_PICKS) {
                break;
            }
            if (pick == null || pick.placeId() == null || pick.reason() == null) {
                continue;
            }
            String id = pick.placeId().trim();
            String reason = pick.reason().trim();
            if (!byId.containsKey(id) || seen.contains(id) || reason.isEmpty() || reason.length() > 300
                    || SpringAIRecommendationService.inventsFacts(reason)) {
                continue;
            }
            seen.add(id);
            picks.add(new Pick(id, reason));
        }
        return new Draft(text, List.copyOf(picks));
    }

    private static String userPrompt(ChatRequest request, List<ChatCandidate> candidates, Optional<ChatResponse.Weather> weather) {
        ChatRequest.Context context = request.context();
        String interests = context.interestsOrEmpty().isEmpty() ? "not given"
                : context.interestsOrEmpty().stream().sorted().map(Interest::name).collect(Collectors.joining(", "));
        String budget = context.maxBudget() == null || context.currencyCode() == null ? "not given"
                : context.currencyCode() + " " + context.maxBudget().setScale(0, RoundingMode.HALF_UP).toPlainString();
        String time = context.availableMinutes() == null ? "not given" : context.availableMinutes() + " minutes";
        String trip = context.tripStopsOrEmpty().isEmpty() ? "nothing planned yet" : String.join("; ", context.tripStopsOrEmpty());
        String forecast = weather.map(w -> "%s, %s°C%s, rain %s".formatted(w.summary(),
                        w.temperatureC() == null ? "?" : String.format(Locale.ROOT, "%.0f", w.temperatureC()),
                        w.isDay() ? "" : " (dark)",
                        w.rainLikely() ? "likely in the next few hours" : "unlikely"))
                .orElse("unknown");
        String places = candidates.isEmpty() ? "none (location not shared or nothing found)"
                : candidates.stream().map(OpenAiChatAssistant::candidateLine).collect(Collectors.joining("\n"));
        String conversation = request.messages().stream()
                .map(turn -> (turn.role() == ChatRequest.Role.USER ? "Traveler: " : "ROAM: ") + turn.content())
                .collect(Collectors.joining("\n"));
        return """
                Traveler context:
                - Area: %s
                - Local time: %s
                - Interests: %s
                - Budget: %s
                - Time available: %s
                - Trip so far: %s
                - Weather: %s

                Nearby places:
                %s

                Conversation (latest last):
                %s
                """.formatted(context.placeName() == null ? "unknown" : context.placeName(), context.localDateTime(),
                interests, budget, time, trip, forecast, places, conversation);
    }

    private static String candidateLine(ChatCandidate candidate) {
        var a = candidate.attraction();
        String categories = a.categories().stream().sorted().map(Interest::name).collect(Collectors.joining(", "));
        String visit = a.estimatedDurationMinutes() == null ? "unknown" : a.estimatedDurationMinutes() + " min (estimate)";
        String cost = a.estimatedCost() == null ? "unknown"
                : a.currencyCode() + " " + a.estimatedCost().setScale(0, RoundingMode.HALF_UP).toPlainString() + " (estimate)";
        return "- id: %s | name: %s | categories: %s | %s | %.1f km | visit: %s | cost: %s".formatted(
                a.id(), a.name(), categories, candidate.setting(), candidate.distanceKm(), visit, cost);
    }

    /** Structured output requested from the model. */
    public record Reply(String reply, List<Pick> suggestions) {
    }
}
