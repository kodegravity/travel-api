package com.aitravel.session.client;

import com.aitravel.session.dto.RecommendationRequest;
import java.util.Set;

/** Mirror of the Recommendation Service's internal contract ({@code POST /internal/v1/recommendations}). */
public record RecommendationSearchRequest(RecommendationRequest request, Set<String> excludedPlaceIds) {
}
