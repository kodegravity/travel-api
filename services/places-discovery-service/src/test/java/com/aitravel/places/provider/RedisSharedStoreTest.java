package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisSharedStoreTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisSharedStore store = new RedisSharedStore(redis, Clock.systemUTC());

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void aNewCounterGetsAnExpiry() {
        when(values.increment("k")).thenReturn(1L);

        assertThat(store.increment("k", Duration.ofDays(2))).hasValue(1L);
        verify(redis).expire("k", Duration.ofDays(2));
    }

    @Test
    void anExistingCounterKeepsItsExpiry() {
        when(values.increment("k")).thenReturn(5L);

        assertThat(store.increment("k", Duration.ofDays(2))).hasValue(5L);
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void anUnreachableRedisIsReportedAsUnavailableNotThrown() {
        when(values.increment("k")).thenThrow(new RedisConnectionFailureException("down"));
        when(values.get("k")).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(store.increment("k", Duration.ofDays(2))).isEmpty();
        assertThat(store.count("k")).isEmpty();
        assertThat(store.get("k")).isEmpty();
    }

    @Test
    void anAbsentCounterCountsAsZero() {
        assertThat(store.count("missing")).hasValue(0L);
    }
}
