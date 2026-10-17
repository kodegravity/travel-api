package com.aitravel.recommendation.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Recommendation tuning, bound from the {@code recommendation.*} keys in application.yml.
 *
 * @param searchRadiusKm      maximum straight-line distance from the traveler to a candidate
 * @param walkingSpeedKmh     speed used to estimate walking time to an attraction
 * @param maxRecommendations  maximum number of recommendations returned
 */
@Validated
@ConfigurationProperties(prefix = "recommendation")
public record RecommendationProperties(
        @DefaultValue("5") @Positive double searchRadiusKm,
        @DefaultValue("4.5") @Positive double walkingSpeedKmh,
        @DefaultValue("3") @Positive int maxRecommendations
) {
}
