package com.aitravel.recommendation.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Hard cap on paid API calls per UTC day, so a bug, a retry loop, or abuse cannot run up the bill.
 *
 * <p>The count is per process. With N tasks the effective daily limit is N times this value, so keep one task
 * or divide the limit when scaling out. A limit of 0 blocks every call.
 */
public final class DailyCallLimiter {

    private final String name;
    private final int maxCallsPerDay;
    private final Clock clock;

    private LocalDate day;
    private int used;

    public DailyCallLimiter(String name, int maxCallsPerDay, Clock clock) {
        if (maxCallsPerDay < 0) {
            throw new IllegalArgumentException(name + " daily call limit must not be negative");
        }
        this.name = name;
        this.maxCallsPerDay = maxCallsPerDay;
        this.clock = clock;
    }

    /** Reserves one call. Returns false, reserving nothing, when today's limit is already used up. */
    public synchronized boolean tryAcquire() {
        rollOver();
        if (used >= maxCallsPerDay) {
            return false;
        }
        used++;
        return true;
    }

    public synchronized int usedToday() {
        rollOver();
        return used;
    }

    public int maxCallsPerDay() {
        return maxCallsPerDay;
    }

    public String name() {
        return name;
    }

    private void rollOver() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
    }
}
