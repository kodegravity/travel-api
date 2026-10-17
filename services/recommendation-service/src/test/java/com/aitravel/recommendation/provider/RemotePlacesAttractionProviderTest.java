package com.aitravel.recommendation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.recommendation.config.RecommendationProperties;
import com.aitravel.recommendation.exception.AttractionProviderException;
import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemotePlacesAttractionProviderTest {

    private static final String BASE_URL = "http://localhost:8082";
    private static final String SEARCH_URL = BASE_URL + "/internal/v1/places/search";
    private static final BigDecimal LAT = new BigDecimal("41.9028");
    private static final BigDecimal LON = new BigDecimal("12.4964");

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RemotePlacesAttractionProvider provider;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new RemotePlacesAttractionProvider(builder.build(), new RecommendationProperties(5.0, 4.5, 3));
    }

    @Test
    void postsCoordinatesAndConfiguredRadiusToSearchEndpoint() {
        server.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.location.latitude").value(41.9028))
                .andExpect(jsonPath("$.location.longitude").value(12.4964))
                .andExpect(jsonPath("$.searchRadiusKm").value(5.0))
                .andRespond(withSuccess("{\"attractions\":[]}", MediaType.APPLICATION_JSON));

        provider.findNearby(LAT, LON);

        server.verify();
    }

    @Test
    void mapsRemoteAttractionsWithoutInventingCostsOrHours() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withSuccess("""
                        {"attractions":[{"id":"colosseum","name":"Colosseum",
                          "categories":["HISTORY"],"latitude":41.8902,"longitude":12.4922,
                          "estimatedCost":null,"currencyCode":null,
                          "estimatedDurationMinutes":90,"description":null}]}
                        """, MediaType.APPLICATION_JSON));

        List<Attraction> result = provider.findNearby(LAT, LON);

        assertThat(result).hasSize(1);
        Attraction colosseum = result.get(0);
        assertThat(colosseum.id()).isEqualTo("colosseum");
        assertThat(colosseum.categories()).containsExactly(Interest.HISTORY);
        assertThat(colosseum.latitude()).isEqualByComparingTo("41.8902");
        assertThat(colosseum.estimatedCost()).isNull();
        assertThat(colosseum.currencyCode()).isNull();
        assertThat(colosseum.estimatedDurationMinutes()).isEqualTo(90);
    }

    @Test
    void returnsEmptyListWhenServiceFindsNothing() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withSuccess("{\"attractions\":[]}", MediaType.APPLICATION_JSON));

        assertThat(provider.findNearby(LAT, LON)).isEmpty();
    }

    @Test
    void nullBodyIsTreatedAsProviderFailureNotEmptyResult() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.findNearby(LAT, LON))
                .isInstanceOf(AttractionProviderException.class);
    }

    @Test
    void upstreamServerErrorIsReportedAsProviderFailure() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.findNearby(LAT, LON))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void badRequestFromServiceIsReportedAsProviderFailure() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> provider.findNearby(LAT, LON))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessageContaining("HTTP 400");
    }

    @Test
    void connectionFailureIsReportedAsProviderFailure() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> provider.findNearby(LAT, LON))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessageContaining("could not be reached");
    }

    @Test
    void readTimeoutIsReportedAsProviderFailure() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> provider.findNearby(LAT, LON))
                .isInstanceOf(AttractionProviderException.class);
    }

    @Test
    void neverFallsBackToMockDataWhenServiceFails() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.findNearby(LAT, LON))
                .isInstanceOf(AttractionProviderException.class);
    }

    @Test
    void slowServiceTripsConfiguredReadTimeout() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/internal/v1/places/search", exchange -> {
            try {
                TimeUnit.SECONDS.sleep(2);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        slow.start();
        try {
            String url = "http://127.0.0.1:" + slow.getAddress().getPort();
            var settings = HttpClientSettings.defaults()
                    .withConnectTimeout(java.time.Duration.ofSeconds(1))
                    .withReadTimeout(java.time.Duration.ofMillis(200));
            RestClient slowClient = RestClient.builder()
                    .baseUrl(url)
                    .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                    .build();
            var slowProvider = new RemotePlacesAttractionProvider(slowClient, new RecommendationProperties(5.0, 4.5, 3));

            assertThatThrownBy(() -> slowProvider.findNearby(LAT, LON))
                    .isInstanceOf(AttractionProviderException.class);
        } finally {
            slow.stop(0);
        }
    }
}
