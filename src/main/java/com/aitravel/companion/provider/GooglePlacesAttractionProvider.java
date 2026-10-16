package com.aitravel.companion.provider;

import com.aitravel.companion.config.GooglePlacesProperties;
import com.aitravel.companion.config.RecommendationProperties;
import com.aitravel.companion.exception.AttractionProviderException;
import com.aitravel.companion.model.Attraction;
import com.aitravel.companion.provider.GooglePlacesApi.Circle;
import com.aitravel.companion.provider.GooglePlacesApi.LatLng;
import com.aitravel.companion.provider.GooglePlacesApi.LocationRestriction;
import com.aitravel.companion.provider.GooglePlacesApi.SearchNearbyRequest;
import com.aitravel.companion.provider.GooglePlacesApi.SearchNearbyResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
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
    private final double searchRadiusMetres;

    public GooglePlacesAttractionProvider(RestClient googlePlacesRestClient,
                                          GooglePlacesProperties properties,
                                          RecommendationProperties recommendationProperties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_PLACES_API_KEY must be set when attraction.provider=google "
                            + "(property google.places.api-key).");
        }
        this.restClient = googlePlacesRestClient;
        this.properties = properties;
        this.searchRadiusMetres = recommendationProperties.searchRadiusKm() * METRES_PER_KM;
    }

    @Override
    public List<Attraction> findNearby(BigDecimal latitude, BigDecimal longitude) {
        SearchNearbyRequest request = new SearchNearbyRequest(
                GooglePlaceTypeMapping.includedTypes(),
                properties.maxResultCount(),
                RANK_BY_DISTANCE,
                new LocationRestriction(new Circle(
                        new LatLng(latitude.doubleValue(), longitude.doubleValue()),
                        searchRadiusMetres)));

        SearchNearbyResponse response = search(request);
        if (response == null || response.places() == null) {
            return List.of();
        }
        return response.places().stream()
                .map(GooglePlaceMapper::toAttraction)
                .flatMap(Optional::stream)
                .toList();
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
        }
    }
}
