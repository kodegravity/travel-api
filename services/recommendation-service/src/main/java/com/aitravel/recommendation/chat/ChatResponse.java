package com.aitravel.recommendation.chat;

import com.aitravel.recommendation.model.Interest;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * The assistant's reply, the places it suggests (always real nearby places), and the weather it took into account.
 *
 * @param aiWritten false when the AI was unavailable and the reply and picks are rule-based
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(String reply, List<Suggestion> suggestions, Weather weather, boolean aiWritten) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Suggestion(
            String placeId,
            String name,
            Set<Interest> categories,
            BigDecimal latitude,
            BigDecimal longitude,
            Double distanceKm,
            Integer estimatedDurationMinutes,
            BigDecimal estimatedCost,
            String currencyCode,
            String reason
    ) {
    }

    /**
     * @param summary       "Light rain", "Clear sky"
     * @param rainLikely    rain is likely in the next few hours
     * @param rainChancePct highest chance of rain over the next few hours
     */
    public record Weather(String summary, Double temperatureC, boolean rainLikely, Integer rainChancePct, boolean isDay) {
    }
}
