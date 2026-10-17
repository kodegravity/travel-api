package com.aitravel.places.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.places.provider.GooglePlacesAttractionProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GooglePlacesConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GooglePlacesConfig.class, GooglePlacesAttractionProvider.class);

    @Test
    void googleProviderFailsFastWhenApiKeyIsMissing() {
        runner.withPropertyValues("attraction.provider=google", "google.places.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable root = context.getStartupFailure();
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root.getMessage()).contains("GOOGLE_PLACES_API_KEY");
                });
    }

    @Test
    void googleProviderStartsWhenApiKeyIsPresent() {
        runner.withPropertyValues("attraction.provider=google", "google.places.api-key=test-key")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(GooglePlacesAttractionProvider.class));
    }

    @Test
    void propertiesToStringNeverRevealsTheApiKey() {
        var properties = new GooglePlacesProperties("secret-key-value", "https://places.googleapis.com",
                Duration.ofSeconds(2), Duration.ofSeconds(5), 20, "places.id");

        assertThat(properties.toString()).doesNotContain("secret-key-value").contains("<redacted>");
    }
}
