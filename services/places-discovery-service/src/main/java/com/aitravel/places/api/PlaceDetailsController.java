package com.aitravel.places.api;

import com.aitravel.places.model.PlaceDetails;
import com.aitravel.places.provider.PlaceDetailsProvider;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/places")
public class PlaceDetailsController {

    /** Google place IDs and mock IDs. Anything else never reaches a provider URL. */
    static final String PLACE_ID_PATTERN = "[A-Za-z0-9_-]{1,256}";

    private static final Logger log = LoggerFactory.getLogger(PlaceDetailsController.class);

    private final PlaceDetailsProvider detailsProvider;

    public PlaceDetailsController(PlaceDetailsProvider detailsProvider) {
        this.detailsProvider = detailsProvider;
    }

    @GetMapping("/{placeId}")
    public PlaceDetails details(@PathVariable @Pattern(regexp = PLACE_ID_PATTERN, message = "is not a valid place id") String placeId) {
        log.info("GET /internal/v1/places/{id} received");
        PlaceDetails details = detailsProvider.findDetails(placeId).orElseThrow(() -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No place matches this id.");
            problem.setTitle("Place not found");
            return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
        });
        log.info("GET /internal/v1/places/{id} completed source={}", details.source());
        return details;
    }

    @GetMapping("/{placeId}/photo")
    public PlaceDetails.Photo photo(@PathVariable @Pattern(regexp = PLACE_ID_PATTERN, message = "is not a valid place id") String placeId) {
        log.info("GET /internal/v1/places/{id}/photo received");
        return detailsProvider.findPhoto(placeId).orElseThrow(() -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "This place has no photo.");
            problem.setTitle("No photo");
            return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
        });
    }
}
