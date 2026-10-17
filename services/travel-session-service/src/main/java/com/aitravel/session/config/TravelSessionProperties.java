package com.aitravel.session.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Active Travel Mode settings, bound from {@code travel.session.*}.
 *
 * @param minimumMovementMeters          movement needed before an automatic refresh
 * @param minimumRefreshIntervalMinutes  minimum time between successful evaluations
 * @param inactivityTimeoutMinutes       idle time before a session expires; extended by valid interactions
 * @param maxLifetimeMinutes             absolute session lifetime, regardless of activity
 * @param retentionAfterExpiryMinutes    how long an expired or stopped session is kept so the API can answer 410/409
 * @param maxActiveSessions              capacity limit for concurrently active sessions
 */
@Validated
@ConfigurationProperties(prefix = "travel.session")
public record TravelSessionProperties(
        @DefaultValue("250") @PositiveOrZero double minimumMovementMeters,
        @DefaultValue("5") @PositiveOrZero int minimumRefreshIntervalMinutes,
        @DefaultValue("30") @Positive int inactivityTimeoutMinutes,
        @DefaultValue("120") @Positive int maxLifetimeMinutes,
        @DefaultValue("30") @PositiveOrZero int retentionAfterExpiryMinutes,
        @DefaultValue("100") @Min(1) int maxActiveSessions
) {

    public TravelSessionProperties {
        if (maxLifetimeMinutes < inactivityTimeoutMinutes) {
            throw new IllegalArgumentException(
                    "travel.session.max-lifetime-minutes must be at least travel.session.inactivity-timeout-minutes");
        }
    }
}
