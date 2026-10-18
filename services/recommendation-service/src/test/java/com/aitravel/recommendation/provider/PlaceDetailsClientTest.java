package com.aitravel.recommendation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.recommendation.exception.AttractionProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PlaceDetailsClientTest {

    private static final String DETAILS_URL = "http://localhost:8082/internal/v1/places/ChIJ-abc_123";

    private MockRestServiceServer server;
    private PlaceDetailsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PlaceDetailsClient(builder.build());
    }

    @Test
    void passesTheJsonThroughUnchanged() {
        String json = "{\"placeId\":\"ChIJ-abc_123\",\"name\":\"Royal Palace\",\"rating\":4.7}";
        server.expect(requestTo(DETAILS_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(client.findDetailsJson("ChIJ-abc_123")).contains(json);
    }

    @Test
    void unknownPlaceIsEmpty() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findDetailsJson("ChIJ-abc_123")).isEmpty();
    }

    @Test
    void otherFailuresBecomeProviderExceptions() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.findDetailsJson("ChIJ-abc_123"))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Places Discovery Service failed with HTTP 500");
    }

    @Test
    void passesThePhotoJsonThroughAndNoPhotoIsEmpty() {
        String json = "{\"url\":\"https://lh3.googleusercontent.com/p/small\"}";
        server.expect(requestTo(DETAILS_URL + "/photo")).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        server.expect(requestTo(DETAILS_URL + "/photo")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findPhotoJson("ChIJ-abc_123")).contains(json);
        assertThat(client.findPhotoJson("ChIJ-abc_123")).isEmpty();
    }
}
