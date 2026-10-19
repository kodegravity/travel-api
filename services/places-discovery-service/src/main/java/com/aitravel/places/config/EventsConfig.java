package com.aitravel.places.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Live event settings, and the Ticketmaster HTTP client when {@code events.provider=ticketmaster}. */
@Configuration
@EnableConfigurationProperties(EventsProperties.class)
public class EventsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "events", name = "provider", havingValue = "ticketmaster")
    RestClient ticketmasterRestClient(EventsProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("events.provider=ticketmaster requires TICKETMASTER_API_KEY. "
                    + "Set it, or run with EVENTS_PROVIDER=mock or none.");
        }
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
