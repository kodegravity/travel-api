package com.aitravel.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

// Optional fields are omitted from JSON when unknown, so the API never fabricates values.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendationDto(
        String placeId,
        String name,
        String category,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer estimatedDurationMinutes,
        BigDecimal estimatedCost,
        String currencyCode,
        String mapsUrl
) {
}
