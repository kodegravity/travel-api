package com.aitravel.session.controller;

import com.aitravel.session.client.RouteClient;
import com.aitravel.session.dto.RouteOptimizeRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puts a trip in a short visiting order with street travel times. Each call can cost one Google Routes call;
 * Places Discovery caps them per day and the web app falls back to its own estimate when refused.
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final RouteClient client;

    public RouteController(RouteClient client) {
        this.client = client;
    }

    @PostMapping(value = "/optimize", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> optimize(@Valid @RequestBody RouteOptimizeRequest request) {
        log.info("POST /api/v1/routes/optimize received stops={} mode={}", request.stops().size(), request.mode());
        long distinct = request.stops().stream().map(RouteOptimizeRequest.Stop::id).distinct().count();
        if (distinct != request.stops().size()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Each stop may appear only once.");
            problem.setTitle("Duplicate stops");
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
        }
        String body = client.optimizeJson(request);
        log.info("POST /api/v1/routes/optimize completed");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
