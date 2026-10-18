package com.aitravel.session.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin access for the separately hosted React app. Scoped to the API only, so actuator endpoints are
 * not cross-origin accessible. Credentials are not allowed because the API does not use cookies or sessions.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebCorsConfig implements WebMvcConfigurer {

    private final CorsProperties properties;

    public WebCorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.allowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/v1/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                // Authorization carries the Okta bearer token; browsers preflight it on every authenticated call.
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .allowCredentials(false)
                .maxAge(1800);
    }
}
