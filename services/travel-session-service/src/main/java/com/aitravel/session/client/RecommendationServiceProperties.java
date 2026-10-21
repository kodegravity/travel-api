package com.aitravel.session.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Connection settings for the Recommendation Service, bound from {@code recommendation-service.*}. */
@Validated
@ConfigurationProperties(prefix = "recommendation-service")
public record RecommendationServiceProperties(
        @DefaultValue("http://localhost:8081") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout
) {
}
