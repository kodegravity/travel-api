package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aitravel.places.config.GooglePlaceDetailsProperties;
import com.aitravel.places.config.GooglePlacesProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.PlaceDetails;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GooglePlaceDetailsProviderTest {

    private static final String API_KEY = "test-api-key-do-not-log";
    private static final String BASE_URL = "https://places.googleapis.com";
    private static final String PLACE_ID = "ChIJ-abc_123";
    private static final String DETAILS_URL = BASE_URL + "/v1/places/" + PLACE_ID + "?languageCode=en";
    private static final String PHOTO_NAME = "places/ChIJ-abc_123/photos/AelY_photo-1";
    private static final String PHOTO_URL = BASE_URL + "/v1/" + PHOTO_NAME + "/media?maxWidthPx=1200&skipHttpRedirect=true";

    private static final String DETAILS_JSON = """
            {
              "id": "ChIJ-abc_123",
              "displayName": {"text": "Royal Palace of Madrid", "languageCode": "en"},
              "formattedAddress": "Calle de Bailén, s/n, 28071 Madrid, Spain",
              "location": {"latitude": 40.4179, "longitude": -3.7143},
              "rating": 4.7,
              "userRatingCount": 12400,
              "currentOpeningHours": {"openNow": true},
              "regularOpeningHours": {"weekdayDescriptions": ["Monday: 10:00 AM – 6:00 PM", "Tuesday: 10:00 AM – 6:00 PM"]},
              "websiteUri": "https://www.patrimonionacional.es",
              "googleMapsUri": "https://maps.google.com/?cid=1",
              "editorialSummary": {"text": "Lavish 18th-century palace."},
              "priceLevel": "PRICE_LEVEL_MODERATE",
              "accessibilityOptions": {"wheelchairAccessibleEntrance": true},
              "reviews": [
                {"rating": 5, "text": {"text": "Go at opening."}, "relativePublishTimeDescription": "a month ago",
                 "authorAttribution": {"displayName": "Ana", "uri": "https://www.google.com/maps/contrib/1"}},
                {"rating": 4, "relativePublishTimeDescription": "a week ago"}
              ],
              "photos": [{"name": "places/ChIJ-abc_123/photos/AelY_photo-1", "widthPx": 4000, "heightPx": 3000,
                          "authorAttributions": [{"displayName": "Luis", "uri": "https://www.google.com/maps/contrib/2"}]}]
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private GooglePlaceDetailsProvider provider(int maxCallsPerDay) {
        return provider(maxCallsPerDay, 30);
    }

    private GooglePlaceDetailsProvider provider(int maxCallsPerDay, int photosPerDay) {
        var places = new GooglePlacesProperties(API_KEY, BASE_URL, Duration.ofSeconds(2), Duration.ofSeconds(5), 20,
                "places.id", 190);
        var details = new GooglePlaceDetailsProperties(maxCallsPerDay, "id,displayName,reviews,photos", 1200, 5,
                photosPerDay, 640);
        return new GooglePlaceDetailsProvider(builder.build(), places, details, Clock.systemUTC());
    }

    @Test
    void mapsDetailsAndResolvesOnePhotoWithoutExposingTheKey() {
        server.expect(requestTo(DETAILS_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", API_KEY))
                .andExpect(header("X-Goog-FieldMask", "id,displayName,reviews,photos"))
                .andRespond(withSuccess(DETAILS_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PHOTO_URL))
                .andExpect(header("X-Goog-Api-Key", API_KEY))
                .andRespond(withSuccess("{\"name\":\"x\",\"photoUri\":\"https://lh3.googleusercontent.com/p/abc\"}",
                        MediaType.APPLICATION_JSON));

        PlaceDetails details = provider(30).findDetails(PLACE_ID).orElseThrow();

        server.verify();
        assertThat(details.name()).isEqualTo("Royal Palace of Madrid");
        assertThat(details.address()).isEqualTo("Calle de Bailén, s/n, 28071 Madrid, Spain");
        assertThat(details.latitude()).isEqualByComparingTo(new BigDecimal("40.4179"));
        assertThat(details.rating()).isEqualTo(4.7);
        assertThat(details.userRatingCount()).isEqualTo(12400);
        assertThat(details.openNow()).isTrue();
        assertThat(details.weekdayHours()).hasSize(2);
        assertThat(details.summary()).isEqualTo("Lavish 18th-century palace.");
        assertThat(details.priceLevel()).isEqualTo("PRICE_LEVEL_MODERATE");
        assertThat(details.accessibility().wheelchairAccessibleEntrance()).isTrue();
        assertThat(details.reviews()).singleElement()
                .satisfies(review -> {
                    assertThat(review.authorName()).isEqualTo("Ana");
                    assertThat(review.text()).isEqualTo("Go at opening.");
                    assertThat(review.rating()).isEqualTo(5);
                });
        assertThat(details.photo().url()).isEqualTo("https://lh3.googleusercontent.com/p/abc").doesNotContain(API_KEY);
        assertThat(details.photo().attributions()).extracting(PlaceDetails.Attribution::displayName).containsExactly("Luis");
        assertThat(details.source()).isEqualTo("google");
    }

    @Test
    void aFailedPhotoLookupStillReturnsTheDetails() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess(DETAILS_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PHOTO_URL)).andRespond(withServerError());

        PlaceDetails details = provider(30).findDetails(PLACE_ID).orElseThrow();

        assertThat(details.photo()).isNull();
        assertThat(details.name()).isEqualTo("Royal Palace of Madrid");
    }

    @Test
    void unknownPlaceIsEmpty() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(provider(30).findDetails(PLACE_ID)).isEqualTo(Optional.empty());
    }

    @Test
    void googleErrorBecomesProviderExceptionWithoutTheKey() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provider(30).findDetails(PLACE_ID))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Google Place Details failed with HTTP 500")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void dailyLimitRefusesBeforeCallingGoogle() {
        server.expect(never(), requestTo(Matchers.startsWith(BASE_URL)));

        assertThatThrownBy(() -> provider(0).findDetails(PLACE_ID))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessageContaining("daily call limit");
        server.verify();
    }

    @Test
    void ignoresAPhotoNameThatIsNotAPlacePhotoPath() {
        String tampered = DETAILS_JSON.replace(PHOTO_NAME, "../../v1/other?x=1");
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess(tampered, MediaType.APPLICATION_JSON));

        PlaceDetails details = provider(30).findDetails(PLACE_ID).orElseThrow();

        assertThat(details.photo()).isNull();
        server.verify();
    }

    private static final String PHOTOS_ONLY_JSON = """
            {"photos": [{"name": "places/ChIJ-abc_123/photos/AelY_photo-1", "widthPx": 4000, "heightPx": 3000,
                         "authorAttributions": [{"displayName": "Luis", "uri": "https://www.google.com/maps/contrib/2"}]}]}
            """;
    private static final String CARD_PHOTO_URL = BASE_URL + "/v1/" + PHOTO_NAME + "/media?maxWidthPx=640&skipHttpRedirect=true";

    @Test
    void cardPhotoAsksOnlyForPhotosThenResolvesASmallImage() {
        server.expect(requestTo(DETAILS_URL))
                .andExpect(header("X-Goog-FieldMask", "photos"))
                .andRespond(withSuccess(PHOTOS_ONLY_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(CARD_PHOTO_URL))
                .andRespond(withSuccess("{\"photoUri\":\"https://lh3.googleusercontent.com/p/small\"}", MediaType.APPLICATION_JSON));

        PlaceDetails.Photo photo = provider(30).findPhoto(PLACE_ID).orElseThrow();

        server.verify();
        assertThat(photo.url()).isEqualTo("https://lh3.googleusercontent.com/p/small");
        assertThat(photo.attributions()).extracting(PlaceDetails.Attribution::displayName).containsExactly("Luis");
    }

    @Test
    void aPlaceWithoutPhotosUsesNoPhotoCall() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        GooglePlaceDetailsProvider provider = provider(30, 1);

        assertThat(provider.findPhoto(PLACE_ID)).isEmpty();
        server.verify();
    }

    @Test
    void cardsAndDetailsShareOneDailyPhotoCap() {
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess(PHOTOS_ONLY_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(CARD_PHOTO_URL))
                .andRespond(withSuccess("{\"photoUri\":\"https://lh3.googleusercontent.com/p/small\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(DETAILS_URL)).andRespond(withSuccess(DETAILS_JSON, MediaType.APPLICATION_JSON));
        GooglePlaceDetailsProvider provider = provider(30, 1);

        assertThat(provider.findPhoto(PLACE_ID)).isPresent();
        // The one photo for today is used: details still load, without a photo call, and cards are refused.
        assertThat(provider.findDetails(PLACE_ID).orElseThrow().photo()).isNull();
        assertThatThrownBy(() -> provider.findPhoto(PLACE_ID))
                .isInstanceOf(AttractionProviderException.class)
                .hasMessage("Google Place Photos daily call limit of 1 reached");
        server.verify();
    }
}
