package com.aitravel.recommendation.live;

import com.aitravel.recommendation.exception.AttractionProviderException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Nearby events from the Places Discovery Service ({@code GET /internal/v1/events/nearby}). */
@Component
public class EventsClient {

    private static final Logger log = LoggerFactory.getLogger(EventsClient.class);

    private final RestClient restClient;

    public EventsClient(RestClient placesDiscoveryRestClient) {
        this.restClient = placesDiscoveryRestClient;
    }

    public List<LiveEvent> nearby(double latitude, double longitude, double radiusKm, int hours) {
        log.info("Calling Places Discovery Service for nearby events");
        try {
            LiveEvent[] events = restClient.get()
                    .uri(uri -> uri.path("/internal/v1/events/nearby")
                            .queryParam("latitude", String.format(Locale.ROOT, "%.5f", latitude))
                            .queryParam("longitude", String.format(Locale.ROOT, "%.5f", longitude))
                            .queryParam("radiusKm", radiusKm)
                            .queryParam("hours", hours)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(LiveEvent[].class);
            return events == null ? List.of() : Arrays.asList(events);
        } catch (RestClientResponseException ex) {
            log.warn("Places Discovery Service events failed with HTTP status {}", ex.getStatusCode().value());
            throw new AttractionProviderException("Places Discovery Service failed with HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("Places Discovery Service could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Places Discovery Service could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Places Discovery Service events could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Places Discovery Service response could not be read", ex);
        }
    }
}
