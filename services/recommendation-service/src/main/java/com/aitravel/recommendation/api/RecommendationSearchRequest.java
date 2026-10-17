package com.aitravel.recommendation.api;

import com.aitravel.recommendation.dto.RecommendationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Internal contract ({@code POST /internal/v1/recommendations}) used by the Travel Session Service.
 *
 * @param request          the same payload accepted by the public recommendations API
 * @param excludedPlaceIds place IDs to skip, e.g. attractions already recommended in the session
 */
public record RecommendationSearchRequest(
        @NotNull @Valid RecommendationRequest request,
        @Size(max = 1000) Set<@Size(max = 200) String> excludedPlaceIds
) {
}
