package com.aitravel.recommendation.dto;

import java.util.List;

public record RecommendationResponse(List<RecommendationDto> recommendations) {
}
