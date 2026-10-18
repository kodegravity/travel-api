package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aitravel.places.config.GooglePlacesProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;

/** Uses a real local server that answers slower than the read timeout, to prove timeouts map to a provider error. */
class GooglePlacesTimeoutTest {

    private HttpServer server;

    @BeforeEach
    void startSlowServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/places:searchNearby", exchange -> {
            try {
                TimeUnit.SECONDS.sleep(2);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readTimeoutIsReportedAsAttractionProviderException() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(1))
                .withReadTimeout(Duration.ofMillis(200));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        var properties = new GooglePlacesProperties("test-key", baseUrl, Duration.ofSeconds(1),
                Duration.ofMillis(200), 20, "places.id", 200);
        var provider = new GooglePlacesAttractionProvider(restClient, properties);

        assertThatThrownBy(() -> provider.findNearby(new BigDecimal("41.9"), new BigDecimal("12.5"), 5.0))
                .isInstanceOf(AttractionProviderException.class);
    }
}
