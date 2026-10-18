package com.aitravel.recommendation.api;

import com.aitravel.recommendation.provider.PlaceDetailsClient;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Place details for the Travel Session Service, passed through from Places Discovery. */
@RestController
@RequestMapping("/internal/v1/places")
public class InternalPlaceDetailsController {

    static final String PLACE_ID_PATTERN = "[A-Za-z0-9_-]{1,256}";

    private final PlaceDetailsClient client;

    public InternalPlaceDetailsController(PlaceDetailsClient client) {
        this.client = client;
    }

    @GetMapping(value = "/{placeId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> details(
            @PathVariable @Pattern(regexp = PLACE_ID_PATTERN, message = "is not a valid place id") String placeId) {
        String body = client.findDetailsJson(placeId).orElseThrow(() -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No place matches this id.");
            problem.setTitle("Place not found");
            return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
        });
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
