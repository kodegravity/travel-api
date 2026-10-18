package com.aitravel.session.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.exception.AttractionProviderException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemoteRecommendationServiceTest {

    private static final String BASE_URL = "http://recommendation.test";
    private static final String RESPONSE_JSON = """
            {"recommendations": [{"placeId": "pantheon", "name": "Pantheon", "category": "HISTORY",
              "description": "Ancient temple", "latitude": 41.8986, "longitude": 12.4769,
              "estimatedDurationMinutes": 45, "estimatedCost": 5, "currencyCode": "EUR",
              "mapsUrl": "https://maps.example/pantheon"}]}
            """;

    private MockRestServiceServer server;
    private RemoteRecommendationService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new RemoteRecommendationService(builder.build());
    }

    private static RecommendationRequest request() {
        return new RecommendationRequest(
                new com.aitravel.session.dto.LocationRequest(new BigDecimal("41.9028"), new BigDecimal("12.4964")),
                Set.of(com.aitravel.session.model.Interest.HISTORY),
                new BigDecimal("50"), "EUR", 180);
    }

    @Test
    void postsRequestAndExcludedIdsAndMapsResponse() {
        server.expect(requestTo(BASE_URL + "/internal/v1/recommendations"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.excludedPlaceIds[0]").value("already-seen"))
                .andExpect(jsonPath("$.request.location.latitude").value(41.9028))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        RecommendationResponse response = service.recommend(request(), Set.of("already-seen"));

        server.verify();
        assertThat(response.recommendations()).extracting("placeId").containsExactly("pantheon");
    }

    @Test
    void emptyBodyBecomesAttractionProviderException() {
        server.expect(requestTo(BASE_URL + "/internal/v1/recommendations"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.recommend(request()))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessageContaining("no body");
        server.verify();
    }

    @Test
    void serverErrorBecomesAttractionProviderException() {
        server.expect(requestTo(BASE_URL + "/internal/v1/recommendations")).andRespond(withServerError());

        assertThatThrownBy(() -> service.recommend(request()))
                .isInstanceOf(AttractionProviderException.class);
        server.verify();
    }

    @Test
    void timeoutBecomesAttractionProviderException() {
        server.expect(requestTo(BASE_URL + "/internal/v1/recommendations"))
                .andRespond(request -> {
                    throw new IOException("read timed out", new SocketTimeoutException());
                });

        assertThatThrownBy(() -> service.recommend(request()))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessageContaining("could not be reached");
    }
}
