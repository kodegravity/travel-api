package com.aitravel.session.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.session.dto.LocationRequest;
import com.aitravel.session.dto.RouteOptimizeRequest;
import com.aitravel.session.exception.AttractionProviderException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RouteClientTest {

    private static final String URL = "http://localhost:8081/internal/v1/routes/optimize";
    private static final RouteOptimizeRequest REQUEST = new RouteOptimizeRequest(RouteOptimizeRequest.Mode.WALK,
            new LocationRequest(new BigDecimal("40.4169"), new BigDecimal("-3.7035")),
            List.of(new RouteOptimizeRequest.Stop("palace", new BigDecimal("40.4179"), new BigDecimal("-3.7143")),
                    new RouteOptimizeRequest.Stop("retiro", new BigDecimal("40.4153"), new BigDecimal("-3.6844"))));

    private MockRestServiceServer server;
    private RouteClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RouteClient(builder.build());
    }

    @Test
    void sendsTheTripAndReturnsTheJsonUnchanged() {
        String json = "{\"order\":[\"retiro\",\"palace\"],\"source\":\"google\"}";
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.mode").value("WALK"))
                .andExpect(jsonPath("$.stops[1].id").value("retiro"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThat(client.optimizeJson(REQUEST)).isEqualTo(json);
    }

    @Test
    void failuresBecomeProviderExceptions() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.optimizeJson(REQUEST))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Recommendation Service failed with HTTP 500");
    }
}
