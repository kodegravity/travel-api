package com.aitravel.session.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.session.exception.AttractionProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PlaceDetailsClientTest {

    private static final String URL = "http://localhost:8081/internal/v1/places/pantheon";

    private MockRestServiceServer server;
    private PlaceDetailsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PlaceDetailsClient(builder.build());
    }

    @Test
    void forwardsTheJson() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"name\":\"Pantheon\"}", MediaType.APPLICATION_JSON));
        assertThat(client.findDetailsJson("pantheon")).contains("{\"name\":\"Pantheon\"}");
    }

    @Test
    void notFoundIsEmpty() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(client.findDetailsJson("pantheon")).isEmpty();
    }

    @Test
    void failureIsProviderException() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> client.findDetailsJson("pantheon")).isInstanceOf(AttractionProviderException.class);
    }
}
