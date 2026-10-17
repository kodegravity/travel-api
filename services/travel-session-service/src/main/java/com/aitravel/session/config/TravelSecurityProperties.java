package com.aitravel.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Okta token rules for {@code /api/v1/**}. The issuer and JWKS location are read from
 * {@code spring.security.oauth2.resourceserver.jwt.*}. Set with {@code OKTA_ISSUER} and {@code OKTA_AUDIENCE}.
 *
 * <p>{@code requiredScope} is optional. When set, tokens must carry that scope, for example {@code travel.use}.
 */
@ConfigurationProperties("travel.security")
public record TravelSecurityProperties(String audience, String requiredScope) {
}
