package com.aitravel.recommendation.chat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A small time-limited cache keyed by area: coordinates rounded to about 100 m. Only the rounded key is kept, never
 * the exact position. Holds at most a few hundred areas.
 */
final class AreaCache<T> {

    private static final int MAX_ENTRIES = 300;

    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Entry<T>> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry<T>> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    AreaCache(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    static String key(BigDecimal latitude, BigDecimal longitude) {
        return latitude.setScale(3, RoundingMode.HALF_UP).toPlainString() + ","
                + longitude.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    /** The cached value for the area, or a fresh one from {@code load}. A failed load is not cached. */
    synchronized T get(String key, Supplier<T> load) {
        Instant now = clock.instant();
        Entry<T> entry = entries.get(key);
        if (entry != null && now.isBefore(entry.expiresAt())) {
            return entry.value();
        }
        T value = load.get();
        entries.put(key, new Entry<>(value, now.plus(ttl)));
        return value;
    }

    private record Entry<T>(T value, Instant expiresAt) {
    }
}
