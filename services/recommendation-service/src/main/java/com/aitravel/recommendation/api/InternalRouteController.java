package com.aitravel.recommendation.api;

import com.aitravel.recommendation.provider.RouteClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Route optimization for the Travel Session Service, passed through to Places Discovery. */
@RestController
@RequestMapping("/internal/v1/routes")
public class InternalRouteController {

    private final RouteClient client;

    public InternalRouteController(RouteClient client) {
        this.client = client;
    }

    @PostMapping(value = "/optimize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> optimize(@RequestBody String request) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(client.optimizeJson(request));
    }
}
