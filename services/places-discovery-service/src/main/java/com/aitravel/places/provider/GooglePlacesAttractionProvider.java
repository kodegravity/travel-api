package com.aitravel.places.provider;

import com.aitravel.places.config.GooglePlacesProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.Attraction;
import com.aitravel.places.provider.GooglePlacesApi.Circle;
import com.aitravel.places.provider.GooglePlacesApi.LatLng;
import com.aitravel.places.provider.GooglePlacesApi.LocationRestriction;
import com.aitravel.places.provider.GooglePlacesApi.SearchNearbyRequest;
import com.aitravel.places.provider.GooglePlacesApi.SearchNearbyResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Attraction provider backed by Google Places API (New) {@code places:searchNearby}.
 *
 * <p>The API key is sent only in the {@code X-Goog-Api-Key} header. It is never logged, and error
 * messages include only the HTTP status code.
 */
@Component
@ConditionalOnProperty(prefix = "attraction", name = "provider", havingValue = "google")
public class GooglePlacesAttractionProvider implements AttractionProvider {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesAttractionProvider.class);
    private static final String SEARCH_NEARBY_PATH = "/v1/places:searchNearby";
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";
    private static final String RANK_BY_DISTANCE = "DISTANCE";
    private static final double METRES_PER_KM = 1000.0;

    private final RestClient restClient;
    private final GooglePlacesProperties properties;
    private final DailyCallLimiter dailyCallLimiter;

    public GooglePlacesAttractionProvider(RestClient googlePlacesRestClient, GooglePlacesProperties properties) {
        this(googlePlacesRestClient, properties, SharedStore.NONE);
    }

    @Autowired
    public GooglePlacesAttractionProvider(RestClient googlePlacesRestClient, GooglePlacesProperties properties,
                                          SharedStore shared) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_PLACES_API_KEY must be set when attraction.provider=google "
                            + "(property google.places.api-key).");
        }
        this.restClient = googlePlacesRestClient;
        this.properties = properties;
        this.dailyCallLimiter = new DailyCallLimiter("google-places", properties.maxCallsPerDay(), Clock.systemUTC(),
                shared);
    }

    @Override
    public List<Attraction> findNearby(BigDecimal latitude, BigDecimal longitude, double searchRadiusKm) {
        SearchNearbyRequest request = new SearchNearbyRequest(
                GooglePlaceTypeMapping.includedTypes(),
                properties.maxResultCount(),
                RANK_BY_DISTANCE,
                new LocationRestriction(new Circle(
                        new LatLng(latitude.doubleValue(), longitude.doubleValue()),
                        searchRadiusKm * METRES_PER_KM)));

        log.info("Calling Google Places searchNearby radiusKm={} maxResults={}", searchRadiusKm, properties.maxResultCount());
        if (!dailyCallLimiter.tryAcquire()) {
            log.warn("Google Places daily call limit of {} reached, refusing search", dailyCallLimiter.maxCallsPerDay());
            throw new AttractionProviderException(
                    "Google Places daily call limit of " + dailyCallLimiter.maxCallsPerDay() + " reached", null);
        }
        SearchNearbyResponse response = search(request);
        if (response == null || response.places() == null) {
            log.info("Google Places searchNearby returned no places");
            return List.of();
        }
        List<Attraction> attractions = response.places().stream()
                .map(GooglePlaceMapper::toAttraction)
                .flatMap(Optional::stream)
                .toList();
        log.info("Google Places searchNearby mapped {} of {} places", attractions.size(), response.places().size());
        return attractions;
    }

    private SearchNearbyResponse search(SearchNearbyRequest request) {
        try {
            return restClient.post()
                    .uri(SEARCH_NEARBY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(API_KEY_HEADER, properties.apiKey())
                    .header(FIELD_MASK_HEADER, properties.fieldMask())
                    .body(request)
                    .retrieve()
                    .body(SearchNearbyResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("Google Places searchNearby failed with HTTP status {}", status);
            throw new AttractionProviderException("Google Places request failed with HTTP " + status, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Google Places searchNearby could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Google Places could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Google Places searchNearby response could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Google Places response could not be read", ex);
        }
    }
}
