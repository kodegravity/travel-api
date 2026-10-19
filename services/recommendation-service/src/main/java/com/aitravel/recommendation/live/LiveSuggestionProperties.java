package com.aitravel.recommendation.live;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Live event suggestions, bound from {@code travel.live.*}.
 *
 * @param radiusKm          how far around the traveler to look
 * @param hours             how far ahead to look
 * @param maxCandidates     events offered to the AI, soonest first
 * @param maxSuggestions    events suggested per refresh
 * @param aiMaxCallsPerDay  hard cap on OpenAI calls for live suggestions per UTC day per task; past it, rule-based
 *                          picks are used. gpt-4o-mini at ~1,500 tokens a call: 100 a day is about $1 a month at most
 */
@Validated
@ConfigurationProperties(prefix = "travel.live")
public record LiveSuggestionProperties(
        @DefaultValue("5") @DecimalMin("0.5") @DecimalMax("25") double radiusKm,
        @DefaultValue("6") @Min(1) @Max(24) int hours,
        @DefaultValue("8") @Min(1) @Max(20) int maxCandidates,
        @DefaultValue("2") @Min(1) @Max(5) int maxSuggestions,
        @DefaultValue("100") @PositiveOrZero int aiMaxCallsPerDay
) {
}
