package com.aitravel.session.controller;

import com.aitravel.session.dto.CreateTravelSessionRequest;
import com.aitravel.session.dto.CreateTravelSessionResponse;
import com.aitravel.session.dto.TravelSessionRecommendationResponse;
import com.aitravel.session.dto.TravelSessionRefreshRequest;
import com.aitravel.session.service.TravelSessionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
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

    private final TravelSessionService travelSessionService;

    public TravelSessionController(TravelSessionService travelSessionService) {
        this.travelSessionService = travelSessionService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateTravelSessionResponse> create(
            @Valid @RequestBody CreateTravelSessionRequest request) {
        CreateTravelSessionResponse response = travelSessionService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sessionId}")
                .buildAndExpand(response.sessionId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping(value = "/{sessionId}/recommendations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TravelSessionRecommendationResponse refresh(
            @PathVariable UUID sessionId,
            @Valid @RequestBody TravelSessionRefreshRequest request) {
        return travelSessionService.refresh(sessionId, request.location());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> stop(@PathVariable UUID sessionId) {
        travelSessionService.stop(sessionId);
        return ResponseEntity.noContent().build();
    }
}
