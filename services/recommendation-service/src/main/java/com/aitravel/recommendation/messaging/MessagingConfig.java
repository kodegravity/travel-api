package com.aitravel.recommendation.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** The travel events consumer, only with {@code travel.messaging.enabled=true}. */
@Configuration
@ConditionalOnProperty(prefix = "travel.messaging", name = "enabled", havingValue = "true")
public class MessagingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "travel.ai", name = "enabled", havingValue = "true")
    AiWarmup aiWarmup() {
        return new AiWarmup(Clock.systemUTC(), Duration.ofMinutes(30));
    }

    @Bean
    SessionEventListener sessionEventListener(JsonMapper mapper, ObjectProvider<AiWarmup> warmup, MeterRegistry meters) {
        return new SessionEventListener(mapper, warmup.getIfAvailable(), meters);
    }
}
