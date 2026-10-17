package com.aitravel.recommendation.api;

import com.aitravel.recommendation.dto.RecommendationResponse;
import com.aitravel.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/recommendations")
public class InternalRecommendationController {

    private final RecommendationService recommendationService;

    public InternalRecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public RecommendationResponse recommend(@Valid @RequestBody RecommendationSearchRequest body) {
        Set<String> excluded = body.excludedPlaceIds() == null ? Set.of() : body.excludedPlaceIds();
        return recommendationService.recommend(body.request(), excluded);
    }
}
