package com.aitravel.recommendation.service;

import com.aitravel.recommendation.dto.RecommendationRequest;
import com.aitravel.recommendation.dto.RecommendationResponse;
import java.util.Set;

public interface RecommendationService {

    RecommendationResponse recommend(RecommendationRequest request);

    /** Same pipeline as {@link #recommend(RecommendationRequest)}, but attractions with the given IDs are skipped. */
    RecommendationResponse recommend(RecommendationRequest request, Set<String> excludedPlaceIds);
}
