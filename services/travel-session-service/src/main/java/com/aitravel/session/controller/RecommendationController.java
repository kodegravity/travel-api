package com.aitravel.session.controller;

import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.service.RecommendationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public standalone recommendations endpoint. Delegates to the Recommendation Service. */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public RecommendationResponse recommend(@Valid @RequestBody RecommendationRequest request) {
        log.info("POST /api/v1/recommendations received interests={} availableMinutes={}",
                request.interests(), request.availableMinutes());
        RecommendationResponse response = recommendationService.recommend(request);
        log.info("POST /api/v1/recommendations completed count={}", response.recommendations().size());
        return response;
    }
}
