package com.aitravel.recommendation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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

class RouteClientTest {

    private static final String URL = "http://localhost:8082/internal/v1/routes/optimize";
    private static final String REQUEST = "{\"mode\":\"WALK\",\"stops\":[]}";

    private MockRestServiceServer server;
    private RouteClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RouteClient(builder.build());
    }

    @Test
    void passesRequestAndResponseThroughUnchanged() {
        String json = "{\"order\":[\"a\",\"b\"],\"source\":\"google\"}";
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(content().json(REQUEST))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(client.optimizeJson(REQUEST)).isEqualTo(json);
    }

    @Test
    void failuresBecomeProviderExceptions() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.optimizeJson(REQUEST))
                .isInstanceOf(AttractionProviderException.class).hasMessage("Places Discovery Service failed with HTTP 500");
        assertThatThrownBy(() -> client.optimizeJson(REQUEST))
                .isInstanceOf(AttractionProviderException.class).hasMessage("Places Discovery Service failed with HTTP 400");
    }
}
