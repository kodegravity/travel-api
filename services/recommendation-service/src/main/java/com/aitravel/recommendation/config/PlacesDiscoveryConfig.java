package com.aitravel.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Wires the HTTP client for the Places Discovery Service. */
@Configuration
@EnableConfigurationProperties(PlacesDiscoveryProperties.class)
public class PlacesDiscoveryConfig {

    @Bean
    RestClient placesDiscoveryRestClient(PlacesDiscoveryProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .requestInterceptor(new BearerTokenForwardingInterceptor())
                .build();
    }
}
