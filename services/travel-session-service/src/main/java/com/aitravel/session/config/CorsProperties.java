package com.aitravel.session.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Browser origins allowed to call {@code /api/v1/**}. Set with {@code CORS_ALLOWED_ORIGINS} (comma-separated).
 * Wildcards are rejected, so every origin must be listed explicitly.
 */
@ConfigurationProperties("travel.cors")
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        for (String origin : allowedOrigins) {
            validateOrigin(origin);
        }
    }

    private static void validateOrigin(String origin) {
        if (origin == null || origin.isBlank() || origin.contains("*")) {
            throw new IllegalArgumentException("travel.cors.allowed-origins must list explicit origins; wildcards are not allowed");
        }
        URI uri;
        try {
            uri = URI.create(origin.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("travel.cors.allowed-origins contains an invalid origin");
        }
        boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        boolean originOnly = uri.getHost() != null
                && (uri.getPath() == null || uri.getPath().isEmpty())
                && uri.getQuery() == null
                && uri.getFragment() == null;
        if (!httpScheme || !originOnly) {
            throw new IllegalArgumentException(
                    "travel.cors.allowed-origins entries must be http(s) origins without a path, e.g. https://app.example.com");
        }
    }
}
