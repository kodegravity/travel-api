package com.aitravel.session.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Test helpers for Okta-style access tokens. Tokens are signed with a locally generated RSA key, so no
 * network call to Okta is needed. The test decoder uses the same validators as production.
 */
public final class JwtTestSupport {

    public static final String ISSUER = "https://test.okta.example.com/oauth2/default";
    public static final String AUDIENCE = "api://travel-session-test";
    public static final String ALICE = "alice-sub";
    public static final String BOB = "bob-sub";

    public static final String ISSUER_PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + ISSUER;
    public static final String AUDIENCE_PROPERTY = "travel.security.audience=" + AUDIENCE;

    private static final KeyPair TRUSTED_KEYS = generateKeys();
    private static final KeyPair UNTRUSTED_KEYS = generateKeys();

    private JwtTestSupport() {
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static String validToken(String subject, String... scopes) {
        return token(subject, ISSUER, AUDIENCE, Instant.now().plusSeconds(3600), TRUSTED_KEYS, scopes);
    }

    public static String tokenWithIssuer(String subject, String issuer) {
        return token(subject, issuer, AUDIENCE, Instant.now().plusSeconds(3600), TRUSTED_KEYS);
    }

    public static String tokenWithAudience(String subject, String audience) {
        return token(subject, ISSUER, audience, Instant.now().plusSeconds(3600), TRUSTED_KEYS);
    }

    public static String expiredToken(String subject) {
        return token(subject, ISSUER, AUDIENCE, Instant.now().minusSeconds(300), TRUSTED_KEYS);
    }

    public static String tokenSignedByUnknownKey(String subject) {
        return token(subject, ISSUER, AUDIENCE, Instant.now().plusSeconds(3600), UNTRUSTED_KEYS);
    }

    private static String token(String subject, String issuer, String audience, Instant expiresAt,
                                KeyPair keys, String... scopes) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(expiresAt.minusSeconds(3600)))
                    .expirationTime(Date.from(expiresAt));
            if (scopes.length > 0) {
                claims.claim("scp", List.of(scopes));
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims.build());
            jwt.sign(new RSASSASigner(keys.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign test token", e);
        }
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate test RSA key", e);
        }
    }

    /** Replaces the Okta JWKS decoder with one trusting only the test key, keeping production validators. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class TrustedKeyDecoderConfig {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            RSAPublicKey publicKey = (RSAPublicKey) TRUSTED_KEYS.getPublic();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            decoder.setJwtValidator(OktaJwtValidators.create(ISSUER, AUDIENCE));
            return decoder;
        }
    }

    /** Authenticates every MockMvc request by default, so controller tests do not need to set a token. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class DefaultJwtMockMvcConfig {

        @Bean
        MockMvcBuilderCustomizer defaultJwtRequests() {
            return builder -> builder.defaultRequest(get("/").with(jwt().jwt(j -> j.subject(ALICE))));
        }
    }
}
