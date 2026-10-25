package com.aitravel.session.push;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Web Push settings ({@code travel.push.*}).
 *
 * @param enabled          off by default: no subscriptions are accepted and nothing is sent
 * @param vapidPublicKey   base64url uncompressed P-256 point; the web app subscribes with it
 * @param vapidPrivateKey  base64url private scalar; on AWS it comes from an SSM SecureString parameter
 * @param subject          contact push services can use, {@code mailto:} or {@code https:}
 * @param ttl              how long a push service keeps an undelivered message
 * @param maxSubscriptions browsers per traveler; the oldest is dropped past this
 * @param timeout          per push service request
 */
@ConfigurationProperties("travel.push")
public record PushProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String vapidPublicKey,
        @DefaultValue("") String vapidPrivateKey,
        @DefaultValue("mailto:admin@localhost") String subject,
        @DefaultValue("4h") Duration ttl,
        @DefaultValue("5") int maxSubscriptions,
        @DefaultValue("5s") Duration timeout
) {
}
