package com.aitravel.session.support;

import com.aitravel.session.dto.RecommendationDto;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.service.RecommendationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Deterministic stand-in for the Recommendation Service, so session tests run without the remote service.
 * Returns up to three places from a fixed pool, skipping excluded IDs in the same way as the real pipeline.
 */
public class StubRecommendationService implements RecommendationService {

    private static final List<String> POOL = IntStream.rangeClosed(1, 10).mapToObj(i -> "place-" + i).toList();

    @Override
    public RecommendationResponse recommend(RecommendationRequest request) {
        return recommend(request, Set.of());
    }

    @Override
    public RecommendationResponse recommend(RecommendationRequest request, Set<String> excludedPlaceIds) {
        List<RecommendationDto> recommendations = POOL.stream()
                .filter(id -> !excludedPlaceIds.contains(id))
                .limit(3)
                .map(StubRecommendationService::dto)
                .toList();
        return new RecommendationResponse(recommendations);
    }

    private static RecommendationDto dto(String placeId) {
        return new RecommendationDto(placeId, "Place " + placeId, "CULTURE", "Stub place", new BigDecimal("41.9028"),
                new BigDecimal("12.4964"), 30, BigDecimal.TEN, "EUR", "https://maps.example/" + placeId);
    }
}
