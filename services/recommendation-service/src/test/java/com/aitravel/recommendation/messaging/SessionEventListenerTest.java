package com.aitravel.recommendation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SessionEventListenerTest {

    private static final String STARTED = """
            {"eventId": "e-1", "eventType": "TravelSessionStarted", "eventVersion": 1,
             "timestamp": "2026-10-19T12:00:00Z", "correlationId": "req-1",
             "payload": {"sessionId": "s1", "ownerRef": "abc", "interests": ["ART"]}, "futureField": true}
            """;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AiWarmup warmup = mock(AiWarmup.class);

    private double count(String outcome) {
        var counter = meters.find("travel.events.consumed").tags("consumer", "recommendation", "outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void warmsUpOncePerEventAndIgnoresRedeliveries() {
        when(warmup.warm()).thenReturn(true);
        SessionEventListener listener = new SessionEventListener(JsonMapper.builder().build(), warmup, meters);

        listener.onMessage(STARTED);
        listener.onMessage(STARTED);

        verify(warmup, times(1)).warm();
        assertThat(count("success")).isEqualTo(1);
        assertThat(count("duplicate")).isEqualTo(1);
    }

    @Test
    void withAiOffTheEventIsStillAcknowledged() {
        new SessionEventListener(JsonMapper.builder().build(), null, meters).onMessage(STARTED);

        assertThat(count("success")).isEqualTo(1);
    }

    @Test
    void unexpectedOrUnreadableMessagesThrowForRetryAndDeadLettering() {
        SessionEventListener listener = new SessionEventListener(JsonMapper.builder().build(), warmup, meters);

        assertThatThrownBy(() -> listener.onMessage("nope")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> listener.onMessage(STARTED.replace("TravelSessionStarted", "TravelSessionEnded")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theWarmupBuildsTheSchemasAndRunsAtMostOncePerInterval() {
        var clock = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-10-19T12:00:00Z"));
        AiWarmup real = new AiWarmup(new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return clock.get();
            }
        }, Duration.ofMinutes(30));

        assertThat(real.warm()).isTrue();
        assertThat(real.warm()).isFalse();
        clock.set(clock.get().plus(Duration.ofMinutes(31)));
        assertThat(real.warm()).isTrue();
    }
}
