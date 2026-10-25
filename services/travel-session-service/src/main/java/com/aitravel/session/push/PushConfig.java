package com.aitravel.session.push;

import java.security.GeneralSecurityException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires Web Push. With {@code travel.push.enabled=false} (the default), or keys missing or unreadable, the service
 * still starts: the web app is told push is off and nothing is sent.
 */
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    PushService pushService(PushProperties properties, PushSubscriptionStore store, JsonMapper mapper, Clock clock) {
        return new PushService(properties, vapid(properties), store, new HttpPushGateway(properties.timeout()), mapper,
                clock);
    }

    private static Vapid vapid(PushProperties properties) {
        if (!properties.enabled()) {
            return null;
        }
        if (properties.vapidPublicKey().isBlank() || properties.vapidPrivateKey().isBlank()) {
            log.warn("Web Push enabled without VAPID keys; push stays off");
            return null;
        }
        try {
            return new Vapid(properties.vapidPublicKey(), properties.vapidPrivateKey(), properties.subject());
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            log.warn("VAPID keys unreadable; push stays off cause={}", ex.getClass().getSimpleName());
            return null;
        }
    }
}
