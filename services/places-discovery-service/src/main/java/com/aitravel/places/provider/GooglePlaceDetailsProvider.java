package com.aitravel.places.provider;

import com.aitravel.places.config.GooglePlaceDetailsProperties;
import com.aitravel.places.config.GooglePlacesProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.PlaceDetails;
import com.aitravel.places.provider.GooglePlacesApi.PhotoMedia;
import com.aitravel.places.provider.GooglePlacesApi.PlaceDetailsResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Place details from Google Places API (New) {@code GET /v1/places/{id}}, plus at most one photo.
 *
 * <p>The API key goes only in the {@code X-Goog-Api-Key} header and is never logged. The photo is resolved with
 * {@code skipHttpRedirect=true}, so the browser gets a short-lived image URL that carries no key. Nothing is cached:
 * Google's terms allow storing place IDs, not place content.
 *
 * <p>Every photo, for the details page or a place card, counts against one shared daily photo cap. A card photo needs
 * the photo's name first, from a details call asking only for {@code photos}: Google bills that field mask at the free
 * Place Details Essentials (IDs Only) rate, so the photo itself is the only billed call.
 */
@Component
@ConditionalOnProperty(prefix = "attraction", name = "provider", havingValue = "google")
public class GooglePlaceDetailsProvider implements PlaceDetailsProvider {

    private static final Logger log = LoggerFactory.getLogger(GooglePlaceDetailsProvider.class);
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";

    private static final String NO_PHOTO_PREFIX = "roam:nophoto:";
    /** Places rarely gain a first photo, and a week keeps the remembered set small. */
    static final Duration NO_PHOTO_TTL = Duration.ofDays(7);

    private final RestClient restClient;
    private final GooglePlacesProperties places;
    private final GooglePlaceDetailsProperties details;
    private final DailyCallLimiter dailyCallLimiter;
    private final DailyCallLimiter photoLimiter;
    private final SharedStore shared;

    // Two constructors: Spring must be told which one to use.
    @Autowired
    public GooglePlaceDetailsProvider(RestClient googlePlacesRestClient, GooglePlacesProperties places,
                                      GooglePlaceDetailsProperties details, SharedStore shared) {
        this(googlePlacesRestClient, places, details, Clock.systemUTC(), shared);
    }

    GooglePlaceDetailsProvider(RestClient restClient, GooglePlacesProperties places,
                               GooglePlaceDetailsProperties details, Clock clock) {
        this(restClient, places, details, clock, SharedStore.NONE);
    }

    GooglePlaceDetailsProvider(RestClient restClient, GooglePlacesProperties places,
                               GooglePlaceDetailsProperties details, Clock clock, SharedStore shared) {
        this.restClient = restClient;
        this.places = places;
        this.details = details;
        this.shared = shared;
        this.dailyCallLimiter = new DailyCallLimiter("google-place-details", details.maxCallsPerDay(), clock, shared);
        this.photoLimiter = new DailyCallLimiter("google-place-photos", details.photosMaxPerDay(), clock, shared);
    }

    @Override
    public Optional<PlaceDetails> findDetails(String placeId) {
        if (!dailyCallLimiter.tryAcquire()) {
            log.warn("Google Place Details daily call limit of {} reached, refusing lookup", dailyCallLimiter.maxCallsPerDay());
            throw new AttractionProviderException(
                    "Google Place Details daily call limit of " + dailyCallLimiter.maxCallsPerDay() + " reached", null);
        }
        log.info("Calling Google Place Details");
        PlaceDetailsResponse response = fetch(placeId, details.fieldMask());
        if (response == null || response.id() == null || response.displayName() == null) {
            return Optional.empty();
        }
        return Optional.of(toDetails(response, firstPhoto(response, details.photoMaxWidthPx())));
    }

    @Override
    public Optional<PlaceDetails.Photo> findPhoto(String placeId) {
        if (photoLimiter.usedToday() >= photoLimiter.maxCallsPerDay()) {
            throw photoLimitReached();
        }
        if (shared.get(NO_PHOTO_PREFIX + placeId).isPresent()) {
            log.info("Place known to have no photo, skipping Google Place Details");
            return Optional.empty();
        }
        log.info("Calling Google Place Details for a card photo");
        PlaceDetailsResponse response = fetch(placeId, "photos");
        if (response == null || response.photos() == null || response.photos().isEmpty()) {
            // Only the place ID is kept (which Google's terms allow), so the next card skips this paid lookup.
            shared.put(NO_PHOTO_PREFIX + placeId, "1", NO_PHOTO_TTL);
            return Optional.empty();
        }
        if (!photoLimiter.tryAcquire()) {
            throw photoLimitReached();
        }
        return Optional.ofNullable(mediaFor(response.photos().get(0), details.cardPhotoMaxWidthPx()));
    }

    private AttractionProviderException photoLimitReached() {
        log.warn("Google Place Photos daily call limit of {} reached, refusing photo", photoLimiter.maxCallsPerDay());
        return new AttractionProviderException(
                "Google Place Photos daily call limit of " + photoLimiter.maxCallsPerDay() + " reached", null);
    }

    /** The place, or null when Google does not know it. */
    private PlaceDetailsResponse fetch(String placeId, String fieldMask) {
        try {
            return restClient.get()
                    .uri(uri -> uri.path("/v1/places/{id}").queryParam("languageCode", "en").build(placeId))
                    .header(API_KEY_HEADER, places.apiKey())
                    .header(FIELD_MASK_HEADER, fieldMask)
                    .retrieve()
                    .body(PlaceDetailsResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == HttpStatus.NOT_FOUND.value()) {
                log.info("Google Place Details has no such place");
                return null;
            }
            log.warn("Google Place Details failed with HTTP status {}", status);
            throw new AttractionProviderException("Google Place Details failed with HTTP " + status, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Google Place Details could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Google Place Details could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Google Place Details response could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Google Place Details response could not be read", ex);
        }
    }

    /** Best effort: a failed or capped photo lookup leaves the page without a photo instead of failing it. */
    private PlaceDetails.Photo firstPhoto(PlaceDetailsResponse response, int maxWidthPx) {
        if (response.photos() == null || response.photos().isEmpty()) {
            return null;
        }
        if (!photoLimiter.tryAcquire()) {
            log.warn("Google Place Photos daily call limit of {} reached, details go without a photo",
                    photoLimiter.maxCallsPerDay());
            return null;
        }
        return mediaFor(response.photos().get(0), maxWidthPx);
    }

    /** A short-lived image URL for the photo, or null when the name is not a photo path or the lookup fails. */
    private PlaceDetails.Photo mediaFor(GooglePlacesApi.Photo photo, int maxWidthPx) {
        if (photo.name() == null || !photo.name().matches("places/[A-Za-z0-9_-]+/photos/[A-Za-z0-9_-]+")) {
            return null;
        }
        try {
            PhotoMedia media = restClient.get()
                    .uri(uri -> uri.path("/v1/" + photo.name() + "/media")
                            .queryParam("maxWidthPx", maxWidthPx)
                            .queryParam("skipHttpRedirect", true)
                            .build())
                    .header(API_KEY_HEADER, places.apiKey())
                    .retrieve()
                    .body(PhotoMedia.class);
            if (media == null || media.photoUri() == null || !media.photoUri().startsWith("https://")) {
                return null;
            }
            List<PlaceDetails.Attribution> attributions = photo.authorAttributions() == null
                    ? List.of()
                    : photo.authorAttributions().stream()
                            .map(a -> new PlaceDetails.Attribution(a.displayName(), a.uri()))
                            .toList();
            return new PlaceDetails.Photo(media.photoUri(), photo.widthPx(), photo.heightPx(), attributions);
        } catch (RestClientException ex) {
            log.warn("Google Place Photo lookup failed ({}), continuing without a photo", ex.getClass().getSimpleName());
            return null;
        }
    }

    private PlaceDetails toDetails(PlaceDetailsResponse r, PlaceDetails.Photo photo) {
        List<PlaceDetails.Review> reviews = r.reviews() == null ? null : r.reviews().stream()
                .filter(review -> review.text() != null && review.text().text() != null)
                .limit(details.maxReviews())
                .map(review -> new PlaceDetails.Review(
                        review.authorAttribution() == null ? null : review.authorAttribution().displayName(),
                        review.authorAttribution() == null ? null : review.authorAttribution().uri(),
                        review.rating(),
                        review.text().text(),
                        review.relativePublishTimeDescription()))
                .toList();
        PlaceDetails.Accessibility accessibility = r.accessibilityOptions() == null ? null : new PlaceDetails.Accessibility(
                r.accessibilityOptions().wheelchairAccessibleEntrance(),
                r.accessibilityOptions().wheelchairAccessibleRestroom(),
                r.accessibilityOptions().wheelchairAccessibleSeating(),
                r.accessibilityOptions().wheelchairAccessibleParking());
        return new PlaceDetails(
                r.id(),
                r.displayName().text(),
                r.formattedAddress(),
                r.location() == null ? null : BigDecimal.valueOf(r.location().latitude()),
                r.location() == null ? null : BigDecimal.valueOf(r.location().longitude()),
                r.rating(),
                r.userRatingCount(),
                r.currentOpeningHours() == null ? null : r.currentOpeningHours().openNow(),
                r.regularOpeningHours() == null ? null : r.regularOpeningHours().weekdayDescriptions(),
                r.websiteUri(),
                r.googleMapsUri(),
                r.editorialSummary() == null ? null : r.editorialSummary().text(),
                r.priceLevel(),
                accessibility,
                photo,
                reviews == null || reviews.isEmpty() ? null : reviews,
                "google");
    }
}
