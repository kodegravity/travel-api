package com.aitravel.session.plan;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in traveler's trips and itineraries, synced across devices. Each traveler only ever sees their own. */
@RestController
@RequestMapping("/api/v1/travel-plan")
public class TravelPlanController {

    private static final Logger log = LoggerFactory.getLogger(TravelPlanController.class);

    private final TravelPlanService travelPlanService;

    public TravelPlanController(TravelPlanService travelPlanService) {
        this.travelPlanService = travelPlanService;
    }

    @GetMapping
    public TravelPlanResponse get(@AuthenticationPrincipal Jwt jwt) {
        log.info("GET /api/v1/travel-plan received");
        TravelPlanResponse response = travelPlanService.get(jwt.getSubject());
        log.info("GET /api/v1/travel-plan completed version={} entries={}", response.version(), response.entries().size());
        return response;
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public TravelPlanResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveTravelPlanRequest request) {
        log.info("PUT /api/v1/travel-plan received baseVersion={} entries={}", request.baseVersion(),
                request.entries().size());
        TravelPlanResponse response = travelPlanService.save(jwt.getSubject(), request);
        log.info("PUT /api/v1/travel-plan completed version={}", response.version());
        return response;
    }
}
