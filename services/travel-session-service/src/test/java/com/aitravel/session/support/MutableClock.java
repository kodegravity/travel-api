package com.aitravel.session.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Deterministic clock for time-based tests. Advance it explicitly instead of sleeping. */
public final class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private volatile Instant instant;

    public MutableClock() {
        this(START);
    }

    public MutableClock(Instant start) {
        this.instant = start;
    }

    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    public void reset() {
        instant = START;
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
        return instant;
    }
}
