package com.aitravel.places.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Wires the Google Places HTTP client. Active only when {@code attraction.provider=google}. */
@Configuration
@EnableConfigurationProperties(GooglePlacesProperties.class)
@ConditionalOnProperty(prefix = "attraction", name = "provider", havingValue = "google")
public class GooglePlacesConfig {

    @Bean
    RestClient googlePlacesRestClient(GooglePlacesProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
