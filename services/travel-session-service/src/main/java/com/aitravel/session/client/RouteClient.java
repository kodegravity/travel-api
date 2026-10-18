package com.aitravel.session.client;

import com.aitravel.session.dto.RouteOptimizeRequest;
import com.aitravel.session.exception.AttractionProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Route optimization from the Recommendation Service ({@code POST /internal/v1/routes/optimize}), which passes it
 * through to Places Discovery. The response JSON is forwarded unchanged.
 */
@Component
public class RouteClient {

    private static final Logger log = LoggerFactory.getLogger(RouteClient.class);
    static final String OPTIMIZE_PATH = "/internal/v1/routes/optimize";

    private final RestClient restClient;

    public RouteClient(RestClient recommendationServiceRestClient) {
        this.restClient = recommendationServiceRestClient;
    }

    public String optimizeJson(RouteOptimizeRequest request) {
        log.info("Requesting route optimization from the recommendation service");
        try {
            String body = restClient.post()
                    .uri(OPTIMIZE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new AttractionProviderException("Recommendation Service returned no body", null);
            }
            return body;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("Route optimization failed with HTTP status {}", status);
            throw new AttractionProviderException("Recommendation Service failed with HTTP " + status, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Recommendation Service could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Recommendation Service could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Route optimization could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Recommendation Service response could not be read", ex);
        }
    }
}
