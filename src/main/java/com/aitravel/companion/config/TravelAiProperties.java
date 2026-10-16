package com.aitravel.companion.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * AI recommendation settings, bound from the {@code travel.ai.*} keys in application.yml.
 *
 * @param enabled            whether the LLM-backed recommendation engine is active; the API key is required only then
 * @param model              OpenAI chat model name
 * @param maxCandidates      maximum eligible attractions sent to the model
 * @param maxRecommendations maximum attractions the model may select
 */
@Validated
@ConfigurationProperties(prefix = "travel.ai")
public record TravelAiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("gpt-4o-mini") @NotBlank String model,
        @DefaultValue("10") @Positive int maxCandidates,
        @DefaultValue("3") @Positive int maxRecommendations
) {
}
