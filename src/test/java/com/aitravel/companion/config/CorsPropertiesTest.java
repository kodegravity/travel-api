package com.aitravel.companion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void acceptsExplicitHttpOrigins() {
        CorsProperties properties = new CorsProperties(List.of("http://localhost:5173", "https://app.example.com"));
        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:5173", "https://app.example.com");
    }

    @Test
    void nullBecomesEmptyList() {
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
    }

    @Test
    void rejectsWildcard() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcards are not allowed");
    }

    @Test
    void rejectsOriginWithPathOrNonHttpScheme() {
        assertThatThrownBy(() -> new CorsProperties(List.of("http://localhost:5173/app")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorsProperties(List.of("ftp://example.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorsProperties(List.of("localhost:5173")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
