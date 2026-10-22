package com.aitravel.places.config;

import com.aitravel.places.provider.RedisSharedStore;
import com.aitravel.places.provider.SharedStore;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis when {@code travel.redis.enabled=true}; otherwise every task keeps its own counts and caches. */
@Configuration
public class SharedStoreConfig {

    @Bean
    SharedStore sharedStore(@Value("${travel.redis.enabled:false}") boolean enabled,
                            ObjectProvider<StringRedisTemplate> redis) {
        StringRedisTemplate template = enabled ? redis.getIfAvailable() : null;
        return template == null ? SharedStore.NONE : new RedisSharedStore(template, Clock.systemUTC());
    }
}
