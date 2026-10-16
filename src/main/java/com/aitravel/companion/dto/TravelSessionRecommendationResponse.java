package com.aitravel.companion.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TravelSessionRecommendationResponse(
        UUID sessionId,
        RefreshStatus refreshStatus,
        List<RecommendationDto> recommendations,
        Instant generatedAt
) {
}
