package com.aitravel.recommendation.provider;

import com.aitravel.recommendation.exception.AttractionProviderException;
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
 * Fetches place details from the Places Discovery Service ({@code GET /internal/v1/places/{id}}).
 *
 * <p>The JSON is passed on unchanged: this service adds nothing to details, so it does not keep its own copy of
 * the contract. Failures are raised as {@link AttractionProviderException} (503); an unknown place is empty (404).
 */
@Component
public class PlaceDetailsClient {

    private static final Logger log = LoggerFactory.getLogger(PlaceDetailsClient.class);
    static final String DETAILS_PATH = "/internal/v1/places/{placeId}";

    private final RestClient restClient;

    public PlaceDetailsClient(RestClient placesDiscoveryRestClient) {
        this.restClient = placesDiscoveryRestClient;
    }

    /** Raw JSON body of the details, or empty when the place is unknown. */
    public Optional<String> findDetailsJson(String placeId) {
        log.info("Calling Places Discovery Service for place details");
        try {
            String body = restClient.get()
                    .uri(DETAILS_PATH, placeId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new AttractionProviderException("Places Discovery Service returned no body", null);
            }
            return Optional.of(body);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            log.warn("Places Discovery Service details failed with HTTP status {}", status);
            throw new AttractionProviderException("Places Discovery Service failed with HTTP " + status, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Places Discovery Service could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Places Discovery Service could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Places Discovery Service details could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Places Discovery Service response could not be read", ex);
        }
    }
}
