package com.aitravel.session.service;

import com.aitravel.session.dto.LiveSuggestionDto;
import com.aitravel.session.dto.LiveSuggestionRequest;
import java.util.List;

import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import java.util.Set;

public interface RecommendationService {

    RecommendationResponse recommend(RecommendationRequest request);

    /** Same pipeline as {@link #recommend(RecommendationRequest)}, but attractions with the given IDs are skipped. */
    RecommendationResponse recommend(RecommendationRequest request, Set<String> excludedPlaceIds);

    /** Live event alerts near the traveler. None unless an implementation provides them. */
    default List<LiveSuggestionDto> liveSuggestions(LiveSuggestionRequest request) {
        return List.of();
    }
}
