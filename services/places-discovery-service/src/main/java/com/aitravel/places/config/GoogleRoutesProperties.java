package com.aitravel.places.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Google Routes API settings, bound from {@code google.routes.*}. Uses the Places API key, which must also have the
 * Routes API enabled.
 *
 * <p>Waypoint optimization is billed at the Compute Routes Enterprise rate at worst ($15 per 1,000, 1,000 free a
 * month). At 20 a day the bill stays inside the free tier.
 *
 * @param baseUrl        Google Routes API base URL
 * @param maxCallsPerDay hard cap on route optimizations per UTC day per task
 */
@Validated
@ConfigurationProperties(prefix = "google.routes")
public record GoogleRoutesProperties(
        @DefaultValue("https://routes.googleapis.com") String baseUrl,
        @DefaultValue("20") @PositiveOrZero int maxCallsPerDay
) {
}
