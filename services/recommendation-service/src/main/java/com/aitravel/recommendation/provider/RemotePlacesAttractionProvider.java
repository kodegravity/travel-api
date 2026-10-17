package com.aitravel.recommendation.provider;

import com.aitravel.recommendation.config.RecommendationProperties;
import com.aitravel.recommendation.exception.AttractionProviderException;
import com.aitravel.recommendation.model.Attraction;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Attraction provider that delegates to the Places Discovery Service over HTTP
 * ({@code POST /internal/v1/places/search}).
 *
 * <p>Failures are never replaced with mock data. Any failure is raised as {@link AttractionProviderException},
 * which the API maps to 503. The remote service holds the Google credentials; this class sends none.
 */
@Component
public class RemotePlacesAttractionProvider implements AttractionProvider {

    private static final Logger log = LoggerFactory.getLogger(RemotePlacesAttractionProvider.class);
    static final String SEARCH_PATH = "/internal/v1/places/search";

    private final RestClient restClient;
    private final RecommendationProperties recommendationProperties;

    public RemotePlacesAttractionProvider(RestClient placesDiscoveryRestClient,
                                          RecommendationProperties recommendationProperties) {
        this.restClient = placesDiscoveryRestClient;
        this.recommendationProperties = recommendationProperties;
    }

    @Override
    public List<Attraction> findNearby(BigDecimal latitude, BigDecimal longitude) {
        var request = new SearchRequest(
                new Coordinates(latitude, longitude),
                recommendationProperties.searchRadiusKm());

        SearchResponse response = search(request);
        if (response == null || response.attractions() == null) {
            throw new AttractionProviderException("Places Discovery Service returned no body", null);
        }
        return response.attractions();
    }

    private SearchResponse search(SearchRequest request) {
        try {
            return restClient.post()
                    .uri(SEARCH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("Places Discovery Service search failed with HTTP status {}", status);
            throw new AttractionProviderException("Places Discovery Service failed with HTTP " + status, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Places Discovery Service could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Places Discovery Service could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Places Discovery Service response could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Places Discovery Service response could not be read", ex);
        }
    }

    /** Contract with the Places Discovery Service; keep in sync with {@code PlacesSearchRequest} there. */
    record SearchRequest(Coordinates location, double searchRadiusKm) {
    }

    record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }

    /** Contract with the Places Discovery Service; keep in sync with {@code PlacesSearchResponse} there. */
    record SearchResponse(List<Attraction> attractions) {
    }
}
