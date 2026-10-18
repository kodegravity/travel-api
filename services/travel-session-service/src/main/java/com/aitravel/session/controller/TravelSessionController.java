package com.aitravel.session.controller;

import com.aitravel.session.dto.CreateTravelSessionRequest;
import com.aitravel.session.dto.CreateTravelSessionResponse;
import com.aitravel.session.dto.TravelSessionRecommendationResponse;
import com.aitravel.session.dto.TravelSessionRefreshRequest;
import com.aitravel.session.service.TravelSessionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/travel-sessions")
public class TravelSessionController {

    private static final Logger log = LoggerFactory.getLogger(TravelSessionController.class);

    private final TravelSessionService travelSessionService;

    public TravelSessionController(TravelSessionService travelSessionService) {
        this.travelSessionService = travelSessionService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateTravelSessionResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateTravelSessionRequest request) {
        log.info("POST /api/v1/travel-sessions received interests={} availableMinutes={}",
                request.interests(), request.availableMinutes());
        CreateTravelSessionResponse response = travelSessionService.create(request, jwt.getSubject());
        log.info("POST /api/v1/travel-sessions completed sessionId={}", response.sessionId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sessionId}")
                .buildAndExpand(response.sessionId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping(value = "/{sessionId}/recommendations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TravelSessionRecommendationResponse refresh(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody TravelSessionRefreshRequest request) {
        log.info("POST /api/v1/travel-sessions/{}/recommendations received", sessionId);
        TravelSessionRecommendationResponse response =
                travelSessionService.refresh(sessionId, jwt.getSubject(), request.location());
        log.info("POST /api/v1/travel-sessions/{}/recommendations completed status={} count={}",
                sessionId, response.refreshStatus(), response.recommendations().size());
        return response;
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> stop(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        log.info("DELETE /api/v1/travel-sessions/{} received", sessionId);
        travelSessionService.stop(sessionId, jwt.getSubject());
        log.info("DELETE /api/v1/travel-sessions/{} completed", sessionId);
        return ResponseEntity.noContent().build();
    }
}
