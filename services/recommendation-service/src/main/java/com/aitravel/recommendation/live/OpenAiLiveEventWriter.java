package com.aitravel.recommendation.live;

import com.aitravel.recommendation.config.TravelAiProperties;
import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.model.Interest;
import com.aitravel.recommendation.service.DailyCallLimiter;
import com.aitravel.recommendation.service.SpringAIRecommendationService;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenAI picks the events that best fit the traveler and writes the alert, grounded only in the event data.
 *
 * <p>Same safeguards as the place picks: only supplied event ids are accepted, text that states a clock time, a
 * relative time, an opening claim, or a price is rejected (the app shows the real start time from the data), the
 * call has a timeout, and calls are capped per day. Any failure raises {@link AiRecommendationException}, and the
 * caller falls back to rule-based picks.
 */
@Component
@ConditionalOnProperty(prefix = "travel.ai", name = "enabled", havingValue = "true")
public class OpenAiLiveEventWriter implements LiveEventWriter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLiveEventWriter.class);
    private static final int MAX_HEADLINE = 80;
    private static final int MAX_MESSAGE = 240;
    /** "in 20 minutes", "2 hours", "tonight at": durations and times come from the data, never from the model. */
    private static final Pattern RELATIVE_TIME =
            Pattern.compile("(?i)\\b\\d+\\s*(min|mins|minutes?|h|hrs?|hours?)\\b|\\b(today|tonight|tomorrow)\\s+at\\b");

    private static final String SYSTEM_PROMPT = """
            You alert a traveler to live events happening near them, chosen from a fixed candidate list.
            Rules:
            - Choose only events whose id appears in the candidate list. Never invent events or details.
            - Choose at most the number of events requested, preferring the best fit for the traveler's interests,
              then sooner and closer events. Choose none if nothing fits.
            - For each, write a headline of at most 60 characters and a message of one or two short sentences.
            - Rely only on the data given for that event. Never state a time, a date, a duration, a price, or whether
              tickets are available; the app shows the real start time and ticket link.
            Respond only with the requested JSON structure.
            """;

    private final ChatClient chatClient;
    private final TravelAiProperties aiProperties;
    private final DailyCallLimiter dailyCallLimiter;
    private final Clock clock;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ai-live-events");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public OpenAiLiveEventWriter(ChatClient chatClient, TravelAiProperties aiProperties, LiveSuggestionProperties properties) {
        this(chatClient, aiProperties, properties, Clock.systemUTC());
    }

    OpenAiLiveEventWriter(ChatClient chatClient, TravelAiProperties aiProperties, LiveSuggestionProperties properties,
                          Clock clock) {
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
        this.dailyCallLimiter = new DailyCallLimiter("openai-live", properties.aiMaxCallsPerDay(), clock);
        this.clock = clock;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public List<LiveSuggestion> write(LiveSuggestionRequest request, List<LiveEvent> candidates, int maxSuggestions) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        if (!dailyCallLimiter.tryAcquire()) {
            log.info("AI live events outcome=limit_reached candidates={}", candidates.size());
            throw new AiRecommendationException(
                    "Daily OpenAI live-event limit of " + dailyCallLimiter.maxCallsPerDay() + " reached", null);
        }
        long startedAt = System.nanoTime();
        Future<WriteResponse> future = executor.submit(() -> chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(request, candidates, maxSuggestions))
                .call()
                .entity(WriteResponse.class));
        WriteResponse response;
        try {
            response = future.get(aiProperties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.info("AI live events outcome=timeout latencyMs={}", elapsedMs(startedAt));
            throw new AiRecommendationException("AI live events timed out after " + aiProperties.timeout(), null);
        } catch (ExecutionException ex) {
            log.info("AI live events outcome=provider_error latencyMs={}", elapsedMs(startedAt));
            throw new AiRecommendationException("AI live events request failed", ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new AiRecommendationException("AI live events were interrupted", ex);
        }
        List<LiveSuggestion> validated = validate(response, candidates, maxSuggestions);
        log.info("AI live events outcome=success latencyMs={} candidates={} selected={}", elapsedMs(startedAt),
                candidates.size(), validated.size());
        return validated;
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    List<LiveSuggestion> validate(WriteResponse response, List<LiveEvent> candidates, int maxSuggestions) {
        if (response == null || response.selections() == null) {
            return List.of();
        }
        Map<String, LiveEvent> byId = new LinkedHashMap<>();
        candidates.forEach(event -> byId.putIfAbsent(event.id(), event));
        Set<String> seen = new LinkedHashSet<>();
        List<LiveSuggestion> result = new ArrayList<>();
        for (Selection selection : response.selections()) {
            if (result.size() >= maxSuggestions) {
                break;
            }
            if (selection == null || selection.eventId() == null || selection.headline() == null || selection.message() == null) {
                continue;
            }
            String id = selection.eventId().trim();
            String headline = selection.headline().trim();
            String message = selection.message().trim();
            // An id counts as used only once its text is accepted, so a rejected attempt does not block a clean one.
            if (!byId.containsKey(id) || seen.contains(id) || headline.isEmpty() || message.isEmpty()
                    || headline.length() > MAX_HEADLINE || message.length() > MAX_MESSAGE) {
                continue;
            }
            String text = headline + " " + message;
            if (SpringAIRecommendationService.inventsFacts(text) || RELATIVE_TIME.matcher(text).find()) {
                log.warn("AI live event text rejected for event {}: states a time, duration, opening, or price", id);
                continue;
            }
            seen.add(id);
            result.add(new LiveSuggestion(byId.get(id), headline, message, true));
        }
        return List.copyOf(result);
    }

    private String userPrompt(LiveSuggestionRequest request, List<LiveEvent> candidates, int maxSuggestions) {
        String interests = request.interestsOrEmpty().isEmpty() ? "none given"
                : request.interestsOrEmpty().stream().sorted().map(Interest::name).collect(Collectors.joining(", "));
        Instant now = clock.instant();
        String lines = candidates.stream().map(event -> candidateLine(event, now)).collect(Collectors.joining("\n"));
        return """
                Traveler interests: %s
                Choose up to %d events.

                Candidates:
                %s
                """.formatted(interests, maxSuggestions, lines);
    }

    private static String candidateLine(LiveEvent event, Instant now) {
        String when = event.startsAt() == null ? "start time not announced"
                : event.startsAt().isBefore(now) ? "already started"
                : "starts in about " + Duration.between(now, event.startsAt()).toMinutes() + " minutes";
        String distance = event.distanceKm() == null ? "unknown" : String.format(Locale.ROOT, "%.1f km", event.distanceKm());
        return "- id: %s | name: %s | type: %s | genre: %s | venue: %s | distance: %s | %s".formatted(
                event.id(), event.name(), event.type(), event.genre() == null ? "unknown" : event.genre(),
                event.venueName() == null ? "unknown" : event.venueName(), distance, when);
    }

    /** Structured output requested from the model. */
    public record WriteResponse(List<Selection> selections) {
    }

    public record Selection(String eventId, String headline, String message) {
    }
}
