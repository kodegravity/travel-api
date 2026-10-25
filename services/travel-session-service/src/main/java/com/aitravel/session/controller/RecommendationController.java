package com.aitravel.session.controller;

import com.aitravel.session.dto.PagedRecommendationResponse;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.feedback.PlaceExclusions;
import com.aitravel.session.service.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public standalone recommendations endpoint. Delegates to the Recommendation Service and pages the ranked result.
 *
 * <p>Each page request runs the full search again (one attraction-provider call), because provider results are
 * not stored. The default page size covers a whole Google search (up to 20 places), so one call usually suffices.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationService recommendationService;
    private final PlaceExclusions exclusions;

    public RecommendationController(RecommendationService recommendationService, PlaceExclusions exclusions) {
        this.recommendationService = recommendationService;
        this.exclusions = exclusions;
    }

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PagedRecommendationResponse recommend(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RecommendationRequest request,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "must not be negative") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "must be between 1 and " + MAX_PAGE_SIZE)
            @Max(value = MAX_PAGE_SIZE, message = "must be between 1 and " + MAX_PAGE_SIZE) int size) {
        log.info("POST /api/v1/recommendations received interests={} availableMinutes={} page={} size={}",
                request.interests(), request.availableMinutes(), page, size);
        // Places the traveler disliked are left out before ranking, so the page fills with other places.
        Set<String> excluded = exclusions.excludedPlaceIds(jwt.getSubject());
        RecommendationResponse response = excluded.isEmpty()
                ? recommendationService.recommend(request)
                : recommendationService.recommend(request, excluded);
        PagedRecommendationResponse paged = PagedRecommendationResponse.of(response.recommendations(), page, size);
        log.info("POST /api/v1/recommendations completed total={} returned={}",
                paged.totalElements(), paged.recommendations().size());
        return paged;
    }
}
