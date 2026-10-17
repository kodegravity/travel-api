package com.aitravel.session.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TravelSessionProperties.class)
public class TravelSessionConfig {

    /** Single time source for session expiry, cooldowns, and cleanup. Tests replace it with a controllable clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
