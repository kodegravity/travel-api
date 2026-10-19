package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.places.config.GooglePlacesProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.Attraction;
import com.aitravel.places.model.Interest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GooglePlacesAttractionProviderTest {

    private static final String API_KEY = "test-api-key-do-not-log";
    private static final String BASE_URL = "https://places.googleapis.com";
    private static final String FIELD_MASK = "places.id,places.displayName,places.location,places.types";
    private static final String SEARCH_URL = BASE_URL + "/v1/places:searchNearby";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private GooglePlacesAttractionProvider provider;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        provider = new GooglePlacesAttractionProvider(
                restClientBuilder.build(), properties(API_KEY));
    }

    @Test
    void sendsSearchNearbyRequestWithKeyFieldMaskAndCircle() {
        server.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", API_KEY))
                .andExpect(header("X-Goog-FieldMask", FIELD_MASK))
                .andExpect(jsonPath("$.maxResultCount").value(20))
                .andExpect(jsonPath("$.rankPreference").value("DISTANCE"))
                .andExpect(jsonPath("$.includedTypes", hasItem("tourist_attraction")))
                .andExpect(jsonPath("$.includedTypes", hasItem("museum")))
                .andExpect(jsonPath("$.locationRestriction.circle.radius").value(5000.0))
                .andExpect(jsonPath("$.locationRestriction.circle.center.latitude").value(41.9028))
                .andExpect(jsonPath("$.locationRestriction.circle.center.longitude").value(12.4964))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        provider.findNearby(new BigDecimal("41.9028"), new BigDecimal("12.4964"), 5.0);

        server.verify();
    }

    @Test
    void mapsPlacesWithKnownInterestsAndSkipsUnmappedOnes() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withSuccess("""
                        {"places":[
                          {"id":"place-colosseum","displayName":{"text":"Colosseum","languageCode":"en"},
                           "location":{"latitude":41.8902,"longitude":12.4922},
                           "types":["historical_landmark","tourist_attraction","point_of_interest"],
                           "googleMapsUri":"https://maps.google.com/?cid=1"},
                          {"id":"place-office","displayName":{"text":"Some Office"},
                           "location":{"latitude":41.90,"longitude":12.49},
                           "types":["corporate_office"]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<Attraction> results = provider.findNearby(new BigDecimal("41.9028"), new BigDecimal("12.4964"), 5.0);

        assertThat(results).hasSize(1);
        Attraction colosseum = results.get(0);
        assertThat(colosseum.id()).isEqualTo("place-colosseum");
        assertThat(colosseum.name()).isEqualTo("Colosseum");
        assertThat(colosseum.categories()).containsExactlyInAnyOrder(Interest.HISTORY, Interest.LOCAL_EXPERIENCES);
        assertThat(colosseum.latitude()).isEqualByComparingTo("41.8902");
        assertThat(colosseum.longitude()).isEqualByComparingTo("12.4922");
        assertThat(colosseum.estimatedDurationMinutes()).isEqualTo(45);
        assertThat(colosseum.estimatedCost()).isNull();
        assertThat(colosseum.currencyCode()).isNull();
        server.verify();
    }

    @Test
    void returnsEmptyListWhenGoogleReturnsNoPlaces() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(provider.findNearby(new BigDecimal("41.9028"), new BigDecimal("12.4964"), 5.0)).isEmpty();
    }

    @Test
    void translatesHttpErrorsWithoutLeakingTheKey() {
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"error\":{\"message\":\"API key not valid\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.findNearby(new BigDecimal("41.9028"), new BigDecimal("12.4964"), 5.0))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Google Places request failed with HTTP 403")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void translatesServerErrors() {
        server.expect(requestTo(SEARCH_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.findNearby(new BigDecimal("41.9028"), new BigDecimal("12.4964"), 5.0))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Google Places request failed with HTTP 500");
    }

    @Test
    void refusesToStartWithoutApiKey() {
        assertThatThrownBy(() -> new GooglePlacesAttractionProvider(
                restClientBuilder.build(), properties("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_PLACES_API_KEY")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void dailyCallLimitBlocksFurtherRequestsWithoutCallingGoogle() {
        GooglePlacesAttractionProvider limited = new GooglePlacesAttractionProvider(
                restClientBuilder.build(), properties(API_KEY, 0));

        assertThatThrownBy(() -> limited.findNearby(new BigDecimal("41.9028"), new BigDecimal("12.4964"), 5.0))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessageContaining("daily call limit");
        server.verify();
    }

    private static GooglePlacesProperties properties(String apiKey) {
        return properties(apiKey, 200);
    }

    private static GooglePlacesProperties properties(String apiKey, int maxCallsPerDay) {
        return new GooglePlacesProperties(apiKey, BASE_URL, Duration.ofSeconds(2), Duration.ofSeconds(5), 20, FIELD_MASK,
                maxCallsPerDay);
    }
}
