package com.aitravel.session.client;

import com.aitravel.session.exception.AttractionProviderException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Place details from the Recommendation Service ({@code GET /internal/v1/places/{id}}), which passes them through
 * from Places Discovery. The JSON is forwarded unchanged; this service only authenticates and routes.
 */
@Component
public class PlaceDetailsClient {

    private static final Logger log = LoggerFactory.getLogger(PlaceDetailsClient.class);
    static final String DETAILS_PATH = "/internal/v1/places/{placeId}";

    private final RestClient restClient;

    public PlaceDetailsClient(RestClient recommendationServiceRestClient) {
        this.restClient = recommendationServiceRestClient;
    }

    /** Raw JSON body of the details, or empty when the place is unknown. */
    public Optional<String> findDetailsJson(String placeId) {
        log.info("Requesting place details from the recommendation service");
        try {
            String body = restClient.get()
                    .uri(DETAILS_PATH, placeId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new AttractionProviderException("Recommendation Service returned no body", null);
            }
            return Optional.of(body);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            log.warn("Place details failed with HTTP status {}", status);
            throw new AttractionProviderException("Recommendation Service failed with HTTP " + status, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Recommendation Service could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Recommendation Service could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Place details could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Recommendation Service response could not be read", ex);
        }
    }
}
