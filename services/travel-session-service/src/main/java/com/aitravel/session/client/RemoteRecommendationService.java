package com.aitravel.session.client;

import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.exception.AttractionProviderException;
import com.aitravel.session.service.RecommendationService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the Recommendation Service ({@code POST /internal/v1/recommendations}).
 *
 * <p>Failures are raised as {@link AttractionProviderException}, so clients receive the same 503 response
 * as before the split. Failures are never replaced with fallback results.
 */
@Service
public class RemoteRecommendationService implements RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RemoteRecommendationService.class);
    static final String RECOMMENDATIONS_PATH = "/internal/v1/recommendations";

    private final RestClient restClient;

    public RemoteRecommendationService(RestClient recommendationServiceRestClient) {
        this.restClient = recommendationServiceRestClient;
    }

    @Override
    public RecommendationResponse recommend(RecommendationRequest request) {
        return recommend(request, Set.of());
    }

    @Override
    public RecommendationResponse recommend(RecommendationRequest request, Set<String> excludedPlaceIds) {
        RecommendationResponse response = post(new RecommendationSearchRequest(request, excludedPlaceIds));
        if (response == null) {
            throw new AttractionProviderException("Recommendation Service returned no body", null);
        }
        return response;
    }

    private RecommendationResponse post(RecommendationSearchRequest body) {
        try {
            return restClient.post()
                    .uri(RECOMMENDATIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RecommendationResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("Recommendation Service failed with HTTP status {}", status);
            throw new AttractionProviderException("Recommendation Service failed with HTTP " + status, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Recommendation Service could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Recommendation Service could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Recommendation Service response could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Recommendation Service response could not be read", ex);
        }
    }
}
