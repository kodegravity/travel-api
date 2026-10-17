package com.aitravel.recommendation.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the Places Discovery Service, bound from {@code places.discovery.*}.
 * Used only when {@code attraction.provider=remote}.
 *
 * @param baseUrl        base URL of the Places Discovery Service, for example {@code http://localhost:8082}
 * @param connectTimeout connection timeout for calls to the service
 * @param readTimeout    response timeout. Keep it above the service's own Google timeout (5s by default), so
 *                       upstream failures arrive as the service's error response instead of a client timeout.
 */
@Validated
@ConfigurationProperties(prefix = "places.discovery")
public record PlacesDiscoveryProperties(
        @DefaultValue("http://localhost:8082") @NotBlank String baseUrl,
        @DefaultValue("2s") @NotNull Duration connectTimeout,
        @DefaultValue("8s") @NotNull Duration readTimeout
) {
}
