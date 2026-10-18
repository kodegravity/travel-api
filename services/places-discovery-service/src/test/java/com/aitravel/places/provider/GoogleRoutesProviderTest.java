package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.places.config.GooglePlacesProperties;
import com.aitravel.places.config.GoogleRoutesProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.OptimizedRoute;
import com.aitravel.places.model.RouteRequest;
import com.aitravel.places.model.RouteRequest.Point;
import com.aitravel.places.model.RouteRequest.Stop;
import com.aitravel.places.model.RouteRequest.TravelMode;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleRoutesProviderTest {

    private static final String API_KEY = "test-api-key-do-not-log";
    private static final String BASE_URL = "https://routes.googleapis.com";
    private static final String URL = BASE_URL + "/directions/v2:computeRoutes";

    private static final Point SOL = new Point(40.4169, -3.7035);
    private static final Stop PALACE = new Stop("palace", 40.4179, -3.7143);
    private static final Stop MAYOR = new Stop("mayor", 40.4155, -3.7074);
    private static final Stop RETIRO = new Stop("retiro", 40.4153, -3.6844);
    private static final Stop PRADO = new Stop("prado", 40.4138, -3.6921);

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private GoogleRoutesProvider provider(int maxCallsPerDay) {
        var places = new GooglePlacesProperties(API_KEY, "https://places.googleapis.com", Duration.ofSeconds(2),
                Duration.ofSeconds(5), 20, "places.id", 190);
        return new GoogleRoutesProvider(builder.build(), places, new GoogleRoutesProperties(BASE_URL, maxCallsPerDay),
                Clock.systemUTC());
    }

    private static RouteRequest request(TravelMode mode, Stop... stops) {
        return new RouteRequest(mode, SOL, List.of(stops));
    }

    @Test
    void endsAtTheFarthestStopAndFollowsGooglesOrderForTheRest() {
        // Retiro is farthest from Sol, so it is the destination; intermediates are [palace, mayor, prado].
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", API_KEY))
                .andExpect(header("X-Goog-FieldMask", GoogleRoutesProvider.FIELD_MASK))
                .andExpect(jsonPath("$.travelMode").value("WALK"))
                .andExpect(jsonPath("$.routingPreference").doesNotExist())
                .andExpect(jsonPath("$.optimizeWaypointOrder").value(true))
                .andExpect(jsonPath("$.destination.location.latLng.longitude").value(-3.6844))
                .andExpect(jsonPath("$.intermediates.length()").value(3))
                .andRespond(withSuccess("""
                        {"routes": [{
                          "optimizedIntermediateWaypointIndex": [1, 0, 2],
                          "legs": [
                            {"distanceMeters": 650, "duration": "520s", "polyline": {"encodedPolyline": "a~l~Fjk~uOw"}},
                            {"distanceMeters": 700, "duration": "560s", "polyline": {"encodedPolyline": "b~l"}},
                            {"distanceMeters": 2100, "duration": "1700s", "polyline": {"encodedPolyline": "c~l"}},
                            {"distanceMeters": 800, "duration": "640s", "polyline": {"encodedPolyline": "d~l"}}
                          ]}]}
                        """, MediaType.APPLICATION_JSON));

        OptimizedRoute route = provider(20).optimize(request(TravelMode.WALK, PALACE, MAYOR, RETIRO, PRADO));

        assertThat(route.order()).containsExactly("mayor", "palace", "prado", "retiro");
        assertThat(route.legs()).hasSize(4);
        assertThat(route.legs().get(0).polyline()).isEqualTo("a~l~Fjk~uOw");
        assertThat(route.distanceMeters()).isEqualTo(4250);
        assertThat(route.durationSeconds()).isEqualTo(3420);
        assertThat(route.source()).isEqualTo("google");
        server.verify();
    }

    @Test
    void drivingIsTrafficUnawareAndMissingZeroesAreZero() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.travelMode").value("DRIVE"))
                .andExpect(jsonPath("$.routingPreference").value("TRAFFIC_UNAWARE"))
                .andRespond(withSuccess("""
                        {"routes": [{"optimizedIntermediateWaypointIndex": [0], "legs": [{"duration": "0s"}, {"distanceMeters": 1200, "duration": "300s"}]}]}
                        """, MediaType.APPLICATION_JSON));

        OptimizedRoute route = provider(20).optimize(request(TravelMode.DRIVE, MAYOR, RETIRO));

        assertThat(route.order()).containsExactly("mayor", "retiro");
        assertThat(route.legs().get(0).distanceMeters()).isZero();
        assertThat(route.legs().get(0).polyline()).isNull();
        assertThat(route.durationSeconds()).isEqualTo(300);
    }

    @Test
    void refusesOnceTheDailyCapIsUsedWithoutCallingGoogle() {
        assertThatThrownBy(() -> provider(0).optimize(request(TravelMode.WALK, MAYOR, RETIRO)))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Google Routes daily call limit of 0 reached");
        server.verify();
    }

    @Test
    void googleErrorsAndEmptyAnswersBecomeProviderExceptions() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"routes\": [{\"legs\": [{\"duration\": \"5s\"}]}]}", MediaType.APPLICATION_JSON));
        GoogleRoutesProvider provider = provider(20);

        assertThatThrownBy(() -> provider.optimize(request(TravelMode.WALK, MAYOR, RETIRO)))
                .isInstanceOf(AttractionProviderException.class).hasMessage("Google Routes failed with HTTP 403");
        assertThatThrownBy(() -> provider.optimize(request(TravelMode.WALK, MAYOR, RETIRO)))
                .isInstanceOf(AttractionProviderException.class).hasMessage("Google Routes found no route");
        assertThatThrownBy(() -> provider.optimize(request(TravelMode.WALK, MAYOR, RETIRO)))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Google Routes returned an unexpected number of legs");
    }

    @Test
    void parsesProtobufDurations() {
        assertThat(GoogleRoutesProvider.seconds("754s")).isEqualTo(754);
        assertThat(GoogleRoutesProvider.seconds("12.6s")).isEqualTo(13);
        assertThat(GoogleRoutesProvider.seconds(null)).isZero();
    }
}
