package com.aitravel.recommendation.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** In-memory {@link SharedStore} for tests; switch {@code available} off to act like an unreachable Redis. */
public class FakeSharedStore implements SharedStore {

    public final Map<String, String> values = new HashMap<>();
    public final Map<String, Duration> ttls = new HashMap<>();
    public boolean available = true;

    @Override
    public OptionalLong increment(String key, Duration ttl) {
        if (!available) {
            return OptionalLong.empty();
        }
        long next = Long.parseLong(values.getOrDefault(key, "0")) + 1;
        values.put(key, Long.toString(next));
        ttls.putIfAbsent(key, ttl);
        return OptionalLong.of(next);
    }

    @Override
    public OptionalLong count(String key) {
        return available ? OptionalLong.of(Long.parseLong(values.getOrDefault(key, "0"))) : OptionalLong.empty();
    }

    @Override
    public Optional<String> get(String key) {
        return available ? Optional.ofNullable(values.get(key)) : Optional.empty();
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        if (available) {
            values.put(key, value);
            ttls.put(key, ttl);
        }
    }
}
