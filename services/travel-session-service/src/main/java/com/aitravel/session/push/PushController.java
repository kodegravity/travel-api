package com.aitravel.session.push;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in traveler's push subscriptions, one per browser they turned notifications on in. */
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private static final Logger log = LoggerFactory.getLogger(PushController.class);
    /** One test notification per traveler per window, so the endpoint cannot be used to spam push services. */
    static final Duration TEST_INTERVAL = Duration.ofSeconds(30);

    private final PushService pushService;
    private final Clock clock;
    private final Map<String, Instant> lastTest = new ConcurrentHashMap<>();

    public PushController(PushService pushService, Clock clock) {
        this.pushService = pushService;
        this.clock = clock;
    }

    @GetMapping("/config")
    public PushConfigResponse config(@AuthenticationPrincipal Jwt jwt) {
        return pushService.config(jwt.getSubject());
    }

    @PutMapping(path = "/subscription", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PushConfigResponse subscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubscribeRequest request) {
        log.info("PUT /api/v1/push/subscription received");
        return pushService.subscribe(jwt.getSubject(), request);
    }

    @PostMapping(path = "/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PushConfigResponse unsubscribe(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody UnsubscribeRequest request) {
        log.info("POST /api/v1/push/unsubscribe received");
        return pushService.unsubscribe(jwt.getSubject(), request.endpoint());
    }

    /** Sends "Notifications are on" to every subscribed browser, so the traveler can check it works. */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Integer>> test(@AuthenticationPrincipal Jwt jwt) {
        Instant now = clock.instant();
        Instant previous = lastTest.put(jwt.getSubject(), now);
        if (previous != null && previous.plus(TEST_INTERVAL).isAfter(now)) {
            lastTest.put(jwt.getSubject(), previous);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        int delivered = pushService.notify(jwt.getSubject(), new PushMessage("Notifications are on",
                "ROAM will tell you about new places and live events nearby.", "roam-test", "/profile"));
        return ResponseEntity.ok(Map.of("delivered", delivered));
    }
}
