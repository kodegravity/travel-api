package com.aitravel.recommendation.messaging;

import com.aitravel.recommendation.chat.OpenAiChatAssistant;
import com.aitravel.recommendation.live.OpenAiLiveEventWriter;
import com.aitravel.recommendation.service.SpringAIRecommendationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Prepares the AI code paths before a traveler needs them: builds the JSON schemas Spring AI sends for structured
 * output, which is slow the first time on a small task. Local only: no OpenAI call, no cost. At most once per
 * {@code interval}, however many sessions start.
 */
public class AiWarmup {

    private static final Logger log = LoggerFactory.getLogger(AiWarmup.class);

    private final Clock clock;
    private final Duration interval;
    private Instant lastRun;

    public AiWarmup(Clock clock, Duration interval) {
        this.clock = clock;
        this.interval = interval;
    }

    /** Returns true when it warmed up now, false when it already had recently. */
    public synchronized boolean warm() {
        Instant now = clock.instant();
        if (lastRun != null && now.isBefore(lastRun.plus(interval))) {
            return false;
        }
        long started = System.nanoTime();
        for (Class<?> type : List.of(SpringAIRecommendationService.SelectionResponse.class,
                OpenAiLiveEventWriter.WriteResponse.class, OpenAiChatAssistant.Reply.class)) {
            new BeanOutputConverter<>(type).getFormat();
        }
        lastRun = now;
        log.info("AI paths warmed up latencyMs={}", (System.nanoTime() - started) / 1_000_000);
        return true;
    }
}
