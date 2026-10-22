package com.aitravel.places.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.OptionalLong;

/**
 * Hard cap on paid API calls per UTC day, so a bug, a retry loop, or abuse cannot run up the bill.
 *
 * <p>With a {@link SharedStore} (Redis) the count is shared by every task and survives restarts. Without one, or
 * while it is unreachable, each process counts on its own, so N tasks could make N times the limit. A limit of 0
 * blocks every call.
 */
public final class DailyCallLimiter {

    /** Longer than a day, so a counter outlives its UTC day and then disappears. */
    private static final Duration SHARED_TTL = Duration.ofDays(2);

    private final String name;
    private final int maxCallsPerDay;
    private final Clock clock;
    private final SharedStore shared;

    private LocalDate day;
    private int used;

    public DailyCallLimiter(String name, int maxCallsPerDay, Clock clock) {
        this(name, maxCallsPerDay, clock, SharedStore.NONE);
    }

    public DailyCallLimiter(String name, int maxCallsPerDay, Clock clock, SharedStore shared) {
        if (maxCallsPerDay < 0) {
            throw new IllegalArgumentException(name + " daily call limit must not be negative");
        }
        this.name = name;
        this.maxCallsPerDay = maxCallsPerDay;
        this.clock = clock;
        this.shared = shared;
    }

    /** Reserves one call. Returns false, reserving nothing, when today's limit is already used up. */
    public synchronized boolean tryAcquire() {
        OptionalLong count = shared.increment(sharedKey(), SHARED_TTL);
        if (count.isPresent()) {
            return count.getAsLong() <= maxCallsPerDay;
        }
        rollOver();
        if (used >= maxCallsPerDay) {
            return false;
        }
        used++;
        return true;
    }

    public synchronized int usedToday() {
        OptionalLong count = shared.count(sharedKey());
        if (count.isPresent()) {
            return (int) Math.min(count.getAsLong(), Integer.MAX_VALUE);
        }
        rollOver();
        return used;
    }

    public int maxCallsPerDay() {
        return maxCallsPerDay;
    }

    public String name() {
        return name;
    }

    private String sharedKey() {
        return "roam:cap:" + name + ":" + LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private void rollOver() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
    }
}
