package com.aitravel.session.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless OAuth2 resource server. Every endpoint needs a valid Okta access token except the health check.
 * Unauthenticated requests get 401 and insufficient permissions get 403 from Spring Security.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(TravelSecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TravelSecurityProperties properties) throws Exception {
        String requiredScope = properties.requiredScope();
        http
                // Bearer tokens are sent explicitly in the Authorization header, not by cookie, so CSRF does not apply.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll();
                    // CORS preflight requests carry no credentials.
                    auth.requestMatchers(HttpMethod.OPTIONS, "/api/v1/**").permitAll();
                    if (requiredScope != null && !requiredScope.isBlank()) {
                        auth.requestMatchers("/api/v1/**").hasAuthority("SCOPE_" + requiredScope);
                    } else {
                        auth.requestMatchers("/api/v1/**").authenticated();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter())));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(TravelSecurityProperties properties,
                          @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuer,
                          @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {
        String audience = properties.audience();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("OKTA_ISSUER must be set to the Okta issuer URI");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException("OKTA_AUDIENCE must be set to the API audience configured in Okta");
        }
        // Okta publishes signing keys at {issuer}/v1/keys. Keys are fetched lazily on the first token.
        String keys = jwkSetUri == null || jwkSetUri.isBlank() ? issuer + "/v1/keys" : jwkSetUri;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(keys).build();
        decoder.setJwtValidator(OktaJwtValidators.create(issuer, audience));
        return decoder;
    }

    /** Maps the OAuth scope claim to SCOPE_ authorities and an optional {@code roles} claim to ROLE_ authorities. */
    private static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return authorities;
        });
        return converter;
    }
}
