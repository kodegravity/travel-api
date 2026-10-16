package com.aitravel.companion.service;

import com.aitravel.companion.dto.RecommendationRequest;
import com.aitravel.companion.dto.RecommendationResponse;
import java.util.Set;

public interface RecommendationService {

    RecommendationResponse recommend(RecommendationRequest request);

    /** Same pipeline as {@link #recommend(RecommendationRequest)}, but attractions with the given IDs are skipped. */
    RecommendationResponse recommend(RecommendationRequest request, Set<String> excludedPlaceIds);
}
