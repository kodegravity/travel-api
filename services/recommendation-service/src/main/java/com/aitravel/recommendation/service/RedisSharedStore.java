package com.aitravel.recommendation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link SharedStore} on Redis (ElastiCache Valkey in AWS). Keys start with {@code roam:}. A failing call is logged
 * at most once a minute and reported as "unavailable", so callers carry on with their local state.
 */
public class RedisSharedStore implements SharedStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSharedStore.class);
    private static final Duration LOG_EVERY = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private Instant lastWarning = Instant.MIN;

    public RedisSharedStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public OptionalLong increment(String key, Duration ttl) {
        return call(() -> {
            Long value = redis.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redis.expire(key, ttl);
            }
            return value == null ? OptionalLong.empty() : OptionalLong.of(value);
        }, OptionalLong.empty());
    }

    @Override
    public OptionalLong count(String key) {
        return call(() -> {
            String value = redis.opsForValue().get(key);
            return OptionalLong.of(value == null ? 0L : Long.parseLong(value));
        }, OptionalLong.empty());
    }

    @Override
    public Optional<String> get(String key) {
        return call(() -> Optional.ofNullable(redis.opsForValue().get(key)), Optional.empty());
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        call(() -> {
            redis.opsForValue().set(key, value, ttl);
            return null;
        }, null);
    }

    private <T> T call(Supplier<T> action, T unavailable) {
        try {
            return action.get();
        } catch (RuntimeException ex) {
            warn(ex);
            return unavailable;
        }
    }

    private synchronized void warn(RuntimeException ex) {
        Instant now = clock.instant();
        if (lastWarning == Instant.MIN || now.isAfter(lastWarning.plus(LOG_EVERY))) {
            lastWarning = now;
            log.warn("Redis unavailable ({}), using local state", ex.getClass().getSimpleName());
        }
    }
}
