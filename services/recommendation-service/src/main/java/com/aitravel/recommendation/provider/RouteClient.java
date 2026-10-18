package com.aitravel.recommendation.provider;

import com.aitravel.recommendation.exception.AttractionProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Route optimization from the Places Discovery Service ({@code POST /internal/v1/routes/optimize}). Request and
 * response JSON are passed on unchanged, as for place details; Places Discovery validates the request.
 */
@Component
public class RouteClient {

    private static final Logger log = LoggerFactory.getLogger(RouteClient.class);
    static final String OPTIMIZE_PATH = "/internal/v1/routes/optimize";

    private final RestClient restClient;

    public RouteClient(RestClient placesDiscoveryRestClient) {
        this.restClient = placesDiscoveryRestClient;
    }

    public String optimizeJson(String requestJson) {
        log.info("Calling Places Discovery Service for route optimization");
        try {
            String body = restClient.post()
                    .uri(OPTIMIZE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new AttractionProviderException("Places Discovery Service returned no body", null);
            }
            return body;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("Places Discovery Service route optimization failed with HTTP status {}", status);
            throw new AttractionProviderException("Places Discovery Service failed with HTTP " + status, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Places Discovery Service could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Places Discovery Service could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Places Discovery Service route could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Places Discovery Service response could not be read", ex);
        }
    }
}
