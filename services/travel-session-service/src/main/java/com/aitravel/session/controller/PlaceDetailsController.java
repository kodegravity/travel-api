package com.aitravel.session.controller;

import com.aitravel.session.client.PlaceDetailsClient;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public place details: address, rating, hours, one photo, and reviews when the provider has them.
 * Each call can cost one Google Place Details call plus one photo call; Places Discovery caps them per day.
 */
@RestController
@RequestMapping("/api/v1/places")
public class PlaceDetailsController {

    static final String PLACE_ID_PATTERN = "[A-Za-z0-9_-]{1,256}";

    private static final Logger log = LoggerFactory.getLogger(PlaceDetailsController.class);

    private final PlaceDetailsClient client;

    public PlaceDetailsController(PlaceDetailsClient client) {
        this.client = client;
    }

    @GetMapping(value = "/{placeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> details(
            @PathVariable @Pattern(regexp = PLACE_ID_PATTERN, message = "is not a valid place id") String placeId) {
        log.info("GET /api/v1/places/{id} received");
        String body = client.findDetailsJson(placeId).orElseThrow(() -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No place matches this id.");
            problem.setTitle("Place not found");
            return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
        });
        log.info("GET /api/v1/places/{id} completed");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** One small photo for a place card. Counts against the shared daily Google photo cap in Places Discovery. */
    @GetMapping(value = "/{placeId}/photo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> photo(
            @PathVariable @Pattern(regexp = PLACE_ID_PATTERN, message = "is not a valid place id") String placeId) {
        log.info("GET /api/v1/places/{id}/photo received");
        String body = client.findPhotoJson(placeId).orElseThrow(() -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "This place has no photo.");
            problem.setTitle("No photo");
            return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
        });
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
