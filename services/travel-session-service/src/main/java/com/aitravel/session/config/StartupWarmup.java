package com.aitravel.session.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Warms the request path in the background once the service is ready, so the first real request after a deploy
 * does not time out. On a 0.25 vCPU task the first token check (downloading Okta's signing keys) and the first
 * downstream call each took several seconds, and three cold services in a row exceeded the client timeouts.
 *
 * <p>Failures are logged and ignored; the service works the same without a warm-up. Disabled with
 * {@code travel.warmup.enabled=false} (tests do this).
 */
@Component
@ConditionalOnProperty(prefix = "travel.warmup", name = "enabled", havingValue = "true", matchIfMissing = true)
class StartupWarmup {

    private static final Logger log = LoggerFactory.getLogger(StartupWarmup.class);

    /** Well-formed but unsigned token. Decoding it makes the decoder fetch Okta's keys, then fail as expected. */
    static final String UNSIGNED_TOKEN = base64Url("{\"alg\":\"RS256\",\"kid\":\"warmup\"}") + "."
            + base64Url("{\"sub\":\"warmup\"}") + "." + base64Url("signature");

    private final JwtDecoder jwtDecoder;
    private final RestClient recommendationServiceRestClient;

    StartupWarmup(JwtDecoder jwtDecoder, RestClient recommendationServiceRestClient) {
        this.jwtDecoder = jwtDecoder;
        this.recommendationServiceRestClient = recommendationServiceRestClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        Thread.ofVirtual().name("startup-warmup").start(this::run);
    }

    void run() {
        long started = System.nanoTime();
        step("okta-signing-keys", () -> {
            try {
                jwtDecoder.decode(UNSIGNED_TOKEN);
            } catch (JwtException expected) {
                // The token is meant to be rejected; fetching the keys was the point.
            }
        });
        // Opens the first connection to recommendation. Its health endpoint needs no token.
        step("recommendation", () -> recommendationServiceRestClient.get().uri("/actuator/health").retrieve().toBodilessEntity());
        log.info("Startup warm-up finished in {} ms", (System.nanoTime() - started) / 1_000_000);
    }

    private static void step(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.info("Startup warm-up step {} skipped ({})", name, ex.getClass().getSimpleName());
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
