package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.places.config.EventsProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.LiveEvent;
import com.aitravel.places.model.LiveEvent.EventType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TicketmasterEventProviderTest {

    private static final String KEY = "tm-key-do-not-log";
    private static final String BASE = "https://app.ticketmaster.com";
    private static final Instant FROM = Instant.parse("2026-11-14T15:00:00Z");
    private static final Instant TO = FROM.plus(Duration.ofHours(6));

    private static final String RESPONSE = """
            {"_embedded": {"events": [
              {"id": "G1", "name": "Real Madrid vs Sevilla", "url": "https://www.ticketmaster.es/event/G1",
               "dates": {"start": {"localDate": "2026-11-14", "localTime": "21:00:00", "dateTime": "2026-11-14T20:00:00Z"},
                         "status": {"code": "onsale"}},
               "classifications": [{"primary": true, "segment": {"name": "Sports"}, "genre": {"name": "Soccer"}}],
               "images": [{"url": "https://s1.ticketm.net/a_4_3.jpg", "ratio": "4_3", "width": 305},
                          {"url": "https://s1.ticketm.net/b_16_9.jpg", "ratio": "16_9", "width": 640}],
               "_embedded": {"venues": [{"name": "Estadio Santiago Bernabéu",
                                         "location": {"latitude": "40.4531", "longitude": "-3.6883"}}]}},
              {"id": "C1", "name": "Jazz Night", "url": "http://insecure.example/c1",
               "dates": {"start": {"localDate": "2026-11-14", "localTime": "19:30:00", "dateTime": "2026-11-14T18:30:00Z"},
                         "status": {"code": "onsale"}},
               "classifications": [{"segment": {"name": "Music"}, "genre": {"name": "Undefined"}}],
               "_embedded": {"venues": [{"name": "Café Central", "location": {"latitude": "40.4146", "longitude": "-3.7007"}}]}},
              {"id": "X1", "name": "Cancelled Show",
               "dates": {"start": {"localDate": "2026-11-14", "dateTime": "2026-11-14T17:00:00Z"}, "status": {"code": "cancelled"}}},
              {"id": "F1", "name": "Kids Circus", "dates": {"start": {"localDate": "2026-11-14", "timeTBA": true}},
               "classifications": [{"segment": {"name": "Miscellaneous"}, "genre": {"name": "Family"}}]}
            ]}}
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private TicketmasterEventProvider provider(int maxCallsPerDay) {
        var properties = new EventsProperties("ticketmaster", KEY, BASE, Duration.ofSeconds(2), Duration.ofSeconds(5),
                30, maxCallsPerDay);
        return new TicketmasterEventProvider(builder.build(), properties, Clock.systemUTC());
    }

    @Test
    void mapsEventsSoonestFirstAndDropsCancelledOnes() {
        server.expect(requestTo(Matchers.startsWith(BASE + "/discovery/v2/events.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("apikey", KEY))
                .andExpect(queryParam("latlong", "40.4169,-3.7035"))
                .andExpect(queryParam("radius", "5"))
                .andExpect(queryParam("startDateTime", "2026-11-14T15:00:00Z"))
                .andExpect(queryParam("endDateTime", "2026-11-14T21:00:00Z"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        List<LiveEvent> events = provider(10).findNearby(40.4169, -3.7035, 5, FROM, TO);

        assertThat(events).extracting(LiveEvent::id).containsExactly("C1", "G1", "F1");
        LiveEvent game = events.get(1);
        assertThat(game.type()).isEqualTo(EventType.SPORTS);
        assertThat(game.genre()).isEqualTo("Soccer");
        assertThat(game.venueName()).isEqualTo("Estadio Santiago Bernabéu");
        assertThat(game.startsAt()).isEqualTo(Instant.parse("2026-11-14T20:00:00Z"));
        assertThat(game.localTime()).isEqualTo("21:00:00");
        assertThat(game.distanceKm()).isEqualTo(4.2);
        assertThat(game.imageUrl()).isEqualTo("https://s1.ticketm.net/b_16_9.jpg");
        assertThat(game.source()).isEqualTo("ticketmaster");

        LiveEvent jazz = events.get(0);
        assertThat(jazz.type()).isEqualTo(EventType.MUSIC);
        assertThat(jazz.genre()).isNull();
        assertThat(jazz.url()).isNull();

        LiveEvent circus = events.get(2);
        assertThat(circus.type()).isEqualTo(EventType.FAMILY);
        assertThat(circus.startsAt()).isNull();
        assertThat(circus.localTime()).isNull();
        server.verify();
    }

    @Test
    void anEmptyAnswerIsNoEvents() {
        server.expect(requestTo(Matchers.startsWith(BASE))).andRespond(withSuccess("{\"page\":{}}", MediaType.APPLICATION_JSON));

        assertThat(provider(10).findNearby(40.4, -3.7, 5, FROM, TO)).isEmpty();
    }

    @Test
    void errorsAndTheDailyCapBecomeProviderExceptionsWithoutTheKey() {
        server.expect(requestTo(Matchers.startsWith(BASE))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        TicketmasterEventProvider provider = provider(1);

        assertThatThrownBy(() -> provider.findNearby(40.4, -3.7, 5, FROM, TO))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Ticketmaster failed with HTTP 401")
                .hasMessageNotContaining(KEY);
        assertThatThrownBy(() -> provider.findNearby(40.4, -3.7, 5, FROM, TO))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Ticketmaster daily call limit of 1 reached");
        server.verify();
    }
}
