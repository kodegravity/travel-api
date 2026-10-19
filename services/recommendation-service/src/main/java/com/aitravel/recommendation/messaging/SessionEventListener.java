package com.aitravel.recommendation.messaging;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes TravelSessionStarted: the traveler's first recommendations and chat are about to be requested, so the AI
 * paths are warmed up now, off the request path. Interactive APIs stay synchronous and do not depend on this.
 *
 * <p>Duplicates (SNS and SQS deliver at least once) are dropped by eventId. Warming up is harmless to repeat, so a
 * per-task memory of recent ids is enough. Unreadable messages throw, so SQS retries and then dead-letters them.
 */
public class SessionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SessionEventListener.class);
    static final String CONSUMER = "recommendation";
    private static final int REMEMBERED = 5_000;

    private final JsonMapper mapper;
    private final AiWarmup warmup;
    private final MeterRegistry meters;
    private final Map<String, Boolean> seen = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > REMEMBERED;
        }
    });

    /** @param warmup null when AI is off; events are then acknowledged without work */
    public SessionEventListener(JsonMapper mapper, AiWarmup warmup, MeterRegistry meters) {
        this.mapper = mapper;
        this.warmup = warmup;
        this.meters = meters;
    }

    @SqsListener("${travel.messaging.session-queue}")
    public void onMessage(String body) {
        TravelEvent event = mapper.readValue(body, TravelEvent.class);
        if (event.eventId() == null || !"TravelSessionStarted".equals(event.eventType())) {
            count(event.eventType(), "invalid");
            throw new IllegalArgumentException("Expected a TravelSessionStarted event");
        }
        if (seen.putIfAbsent(event.eventId(), Boolean.TRUE) != null) {
            log.info("Duplicate event ignored eventType={} eventId={}", event.eventType(), event.eventId());
            count(event.eventType(), "duplicate");
            return;
        }
        String sessionId = event.payload() == null || event.payload().get("sessionId") == null
                ? "unknown" : event.payload().get("sessionId").asString();
        boolean warmed = warmup != null && warmup.warm();
        log.info("Session start handled sessionId={} eventId={} correlationId={} aiWarmed={}", sessionId, event.eventId(),
                event.correlationId(), warmed);
        count(event.eventType(), "success");
    }

    private void count(String eventType, String outcome) {
        meters.counter("travel.events.consumed", "consumer", CONSUMER, "eventType", eventType == null ? "unknown" : eventType,
                "outcome", outcome).increment();
    }
}
