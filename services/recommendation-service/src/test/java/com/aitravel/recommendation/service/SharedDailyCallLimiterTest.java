package com.aitravel.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Two limiters on one store stand in for two tasks sharing Redis. */
class SharedDailyCallLimiterTest {

    private static final Clock NOON = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);

    private final FakeSharedStore store = new FakeSharedStore();

    @Test
    void tasksShareOneDailyCap() {
        DailyCallLimiter first = new DailyCallLimiter("google-places", 3, NOON, store);
        DailyCallLimiter second = new DailyCallLimiter("google-places", 3, NOON, store);

        assertThat(first.tryAcquire()).isTrue();
        assertThat(second.tryAcquire()).isTrue();
        assertThat(first.tryAcquire()).isTrue();
        assertThat(second.tryAcquire()).isFalse();
        assertThat(first.usedToday()).isGreaterThanOrEqualTo(3);
        assertThat(store.values).containsKey("roam:cap:google-places:2026-10-21");
        assertThat(store.ttls.get("roam:cap:google-places:2026-10-21")).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void countsLocallyWhileTheStoreIsUnreachable() {
        store.available = false;
        DailyCallLimiter limiter = new DailyCallLimiter("openai", 1, NOON, store);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.usedToday()).isEqualTo(1);
    }
}
