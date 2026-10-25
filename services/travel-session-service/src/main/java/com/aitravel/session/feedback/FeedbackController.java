package com.aitravel.session.feedback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in traveler's likes and dislikes. Each traveler only ever sees their own. */
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);
    /** Google place IDs and the mock provider's IDs: letters, digits, '-', '_', ':'. */
    private static final String PLACE_ID = "[A-Za-z0-9_:-]+";

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    public FeedbackResponse list(@AuthenticationPrincipal Jwt jwt) {
        log.info("GET /api/v1/feedback received");
        return feedbackService.list(jwt.getSubject());
    }

    @PutMapping(path = "/{placeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FeedbackResponse rate(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable @Size(max = 300) @Pattern(regexp = PLACE_ID) String placeId,
                                 @Valid @RequestBody RatePlaceRequest request) {
        log.info("PUT /api/v1/feedback received rating={}", request.rating());
        return feedbackService.rate(jwt.getSubject(), placeId, request);
    }

    @DeleteMapping("/{placeId}")
    public FeedbackResponse clear(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable @Size(max = 300) @Pattern(regexp = PLACE_ID) String placeId) {
        log.info("DELETE /api/v1/feedback received");
        return feedbackService.clear(jwt.getSubject(), placeId);
    }
}
