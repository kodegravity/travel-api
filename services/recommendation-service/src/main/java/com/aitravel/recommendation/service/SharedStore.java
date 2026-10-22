package com.aitravel.recommendation.service;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * State shared by every task of every service: daily call counts and small caches. Backed by Redis
 * ({@code travel.redis.enabled=true}) or by nothing at all. Every method is best effort: when the store is off or
 * unreachable it reports "nothing there" and callers fall back to what they did before, so Redis can never take a
 * request down with it.
 */
public interface SharedStore {

    /** Adds one to the counter, setting it to expire after {@code ttl} when new. Empty when unavailable. */
    OptionalLong increment(String key, Duration ttl);

    /** The counter's value (0 when absent). Empty when unavailable. */
    OptionalLong count(String key);

    /** The cached value. Empty when absent or unavailable. */
    Optional<String> get(String key);

    /** Caches a value until {@code ttl} passes. Does nothing when unavailable. */
    void put(String key, String value, Duration ttl);

    /** No shared store: every caller uses its own in-process state. */
    SharedStore NONE = new SharedStore() {
        @Override
        public OptionalLong increment(String key, Duration ttl) {
            return OptionalLong.empty();
        }

        @Override
        public OptionalLong count(String key) {
            return OptionalLong.empty();
        }

        @Override
        public Optional<String> get(String key) {
            return Optional.empty();
        }

        @Override
        public void put(String key, String value, Duration ttl) {
        }
    };
}
