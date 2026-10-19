package com.aitravel.recommendation.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.recommendation.exception.AttractionProviderException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EventsClientTest {

    private static final String URL =
            "http://localhost:8082/internal/v1/events/nearby?latitude=40.41690&longitude=-3.70350&radiusKm=5.0&hours=6";

    private MockRestServiceServer server;
    private EventsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EventsClient(builder.build());
    }

    @Test
    void readsEvents() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                [{"id":"G1","name":"Derby","type":"SPORTS","startsAt":"2026-11-14T20:00:00Z","distanceKm":4.2,"extra":1}]
                """, MediaType.APPLICATION_JSON));

        assertThat(client.nearby(40.4169, -3.7035, 5, 6)).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(LiveEvent.EventType.SPORTS);
            assertThat(event.startsAt()).isEqualTo(Instant.parse("2026-11-14T20:00:00Z"));
        });
    }

    @Test
    void failuresBecomeProviderExceptions() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.nearby(40.4169, -3.7035, 5, 6))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Places Discovery Service failed with HTTP 500");
    }
}
