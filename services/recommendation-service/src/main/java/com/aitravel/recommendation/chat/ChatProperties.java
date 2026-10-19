package com.aitravel.recommendation.chat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * "Ask ROAM" chat settings, bound from {@code travel.chat.*}.
 *
 * @param aiMaxCallsPerDay hard cap on OpenAI chat replies per UTC day per task; past it, rule-based replies are used.
 *                         gpt-4o-mini at ~2,500 tokens a reply: 200 a day is about $6 a month at most
 * @param maxCandidates    nearby places offered to the AI per reply
 * @param radiusKm         how far around the traveler a place may be
 * @param cacheTtl         how long nearby places and the forecast are reused for the same area, so a conversation
 *                         does not repeat paid searches
 * @param weatherBaseUrl   Open-Meteo forecast API (free, no key)
 */
@Validated
@ConfigurationProperties(prefix = "travel.chat")
public record ChatProperties(
        @DefaultValue("200") @PositiveOrZero int aiMaxCallsPerDay,
        @DefaultValue("15") @Min(3) @Max(30) int maxCandidates,
        @DefaultValue("3") @DecimalMin("0.5") @DecimalMax("10") double radiusKm,
        @DefaultValue("15m") Duration cacheTtl,
        @DefaultValue("https://api.open-meteo.com") String weatherBaseUrl
) {
}
