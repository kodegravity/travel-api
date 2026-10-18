package com.aitravel.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DailyCallLimiterTest {

    private static final Instant NOON = Instant.parse("2026-10-18T12:00:00Z");

    @Test
    void allowsCallsUpToTheLimitThenBlocks() {
        DailyCallLimiter limiter = new DailyCallLimiter("test", 2, Clock.fixed(NOON, ZoneOffset.UTC));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.usedToday()).isEqualTo(2);
    }

    @Test
    void resetsAtUtcMidnight() {
        MovableClock clock = new MovableClock(NOON);
        DailyCallLimiter limiter = new DailyCallLimiter("test", 1, clock);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        clock.advance(Duration.ofHours(12));

        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void zeroLimitBlocksEveryCall() {
        DailyCallLimiter limiter = new DailyCallLimiter("test", 0, Clock.fixed(NOON, ZoneOffset.UTC));

        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void rejectsNegativeLimit() {
        assertThatThrownBy(() -> new DailyCallLimiter("test", -1, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
