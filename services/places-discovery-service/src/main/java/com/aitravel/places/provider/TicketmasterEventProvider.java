package com.aitravel.places.provider;

import com.aitravel.places.config.EventsProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.LiveEvent;
import com.aitravel.places.model.LiveEvent.EventType;
import com.aitravel.places.provider.TicketmasterApi.Classification;
import com.aitravel.places.provider.TicketmasterApi.Event;
import com.aitravel.places.provider.TicketmasterApi.Image;
import com.aitravel.places.provider.TicketmasterApi.SearchResponse;
import com.aitravel.places.provider.TicketmasterApi.Venue;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Events from the Ticketmaster Discovery API: one call per search, within a daily cap.
 *
 * <p>The key goes in the {@code apikey} query parameter, as Ticketmaster requires; request URLs are never logged.
 * Cancelled and postponed events are dropped. Nothing is cached.
 */
@Component
@ConditionalOnProperty(prefix = "events", name = "provider", havingValue = "ticketmaster")
public class TicketmasterEventProvider implements EventProvider {

    private static final Logger log = LoggerFactory.getLogger(TicketmasterEventProvider.class);
    private static final Set<String> DROPPED_STATUSES = Set.of("cancelled", "canceled", "postponed");

    private final RestClient restClient;
    private final EventsProperties properties;
    private final DailyCallLimiter dailyCallLimiter;

    @Autowired
    public TicketmasterEventProvider(@Qualifier("ticketmasterRestClient") RestClient restClient,
                                     EventsProperties properties, SharedStore shared) {
        this(restClient, properties, Clock.systemUTC(), shared);
    }

    TicketmasterEventProvider(RestClient restClient, EventsProperties properties, Clock clock) {
        this(restClient, properties, clock, SharedStore.NONE);
    }

    TicketmasterEventProvider(RestClient restClient, EventsProperties properties, Clock clock, SharedStore shared) {
        this.restClient = restClient;
        this.properties = properties;
        this.dailyCallLimiter = new DailyCallLimiter("ticketmaster", properties.maxCallsPerDay(), clock, shared);
    }

    @Override
    public List<LiveEvent> findNearby(double latitude, double longitude, double radiusKm, Instant from, Instant to) {
        if (!dailyCallLimiter.tryAcquire()) {
            log.warn("Ticketmaster daily call limit of {} reached, refusing event search", dailyCallLimiter.maxCallsPerDay());
            throw new AttractionProviderException(
                    "Ticketmaster daily call limit of " + dailyCallLimiter.maxCallsPerDay() + " reached", null);
        }
        log.info("Calling Ticketmaster event search radiusKm={}", radiusKm);
        SearchResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/discovery/v2/events.json")
                            .queryParam("apikey", properties.apiKey())
                            .queryParam("latlong", String.format(Locale.ROOT, "%.4f,%.4f", latitude, longitude))
                            .queryParam("radius", (int) Math.ceil(radiusKm))
                            .queryParam("unit", "km")
                            .queryParam("startDateTime", from.truncatedTo(ChronoUnit.SECONDS).toString())
                            .queryParam("endDateTime", to.truncatedTo(ChronoUnit.SECONDS).toString())
                            .queryParam("sort", "date,asc")
                            .queryParam("size", properties.pageSize())
                            .queryParam("locale", "*")
                            .build())
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (RestClientResponseException ex) {
            log.warn("Ticketmaster failed with HTTP status {}", ex.getStatusCode().value());
            throw new AttractionProviderException("Ticketmaster failed with HTTP " + ex.getStatusCode().value(), null);
        } catch (ResourceAccessException ex) {
            log.warn("Ticketmaster could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Ticketmaster could not be reached", null);
        } catch (RestClientException ex) {
            log.warn("Ticketmaster response could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Ticketmaster response could not be read", null);
        }
        if (response == null || response.embedded() == null || response.embedded().events() == null) {
            return List.of();
        }
        List<LiveEvent> events = response.embedded().events().stream()
                .filter(Objects::nonNull)
                .filter(event -> event.id() != null && event.name() != null)
                .filter(event -> !dropped(event))
                .map(event -> toLiveEvent(event, latitude, longitude))
                .filter(event -> event.distanceKm() == null || event.distanceKm() <= radiusKm + 0.5)
                .sorted(Comparator.comparing(LiveEvent::startsAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        log.info("Ticketmaster returned {} events", events.size());
        return events;
    }

    private static boolean dropped(Event event) {
        String code = event.dates() == null || event.dates().status() == null ? null : event.dates().status().code();
        return code != null && DROPPED_STATUSES.contains(code.toLowerCase(Locale.ROOT));
    }

    private LiveEvent toLiveEvent(Event event, double fromLatitude, double fromLongitude) {
        Venue venue = event.embedded() == null || event.embedded().venues() == null || event.embedded().venues().isEmpty()
                ? null : event.embedded().venues().get(0);
        Double latitude = venue == null || venue.location() == null ? null : number(venue.location().latitude());
        Double longitude = venue == null || venue.location() == null ? null : number(venue.location().longitude());
        Double distance = latitude != null && longitude != null
                // Boxed on both sides: a mixed ternary would unbox a missing Ticketmaster distance and throw.
                ? Double.valueOf(Math.round(haversineKm(fromLatitude, fromLongitude, latitude, longitude) * 10) / 10.0)
                : event.distance();
        TicketmasterApi.Start start = event.dates() == null ? null : event.dates().start();
        boolean timeKnown = start != null && !Boolean.TRUE.equals(start.timeTBA()) && !Boolean.TRUE.equals(start.dateTBA());
        Classification classification = primary(event.classifications());
        return new LiveEvent(
                event.id(),
                event.name(),
                type(classification),
                classification == null || classification.genre() == null ? null : usable(classification.genre().name()),
                venue == null ? null : venue.name(),
                latitude,
                longitude,
                timeKnown ? instant(start.dateTime()) : null,
                start == null ? null : start.localDate(),
                timeKnown ? start.localTime() : null,
                distance,
                https(event.url()),
                image(event.images()),
                "ticketmaster");
    }

    private static Classification primary(List<Classification> classifications) {
        if (classifications == null || classifications.isEmpty()) {
            return null;
        }
        return classifications.stream().filter(c -> Boolean.TRUE.equals(c.primary())).findFirst()
                .orElse(classifications.get(0));
    }

    static EventType type(Classification classification) {
        if (classification == null) {
            return EventType.OTHER;
        }
        String genre = classification.genre() == null ? "" : String.valueOf(classification.genre().name());
        if (genre.equalsIgnoreCase("Family") || genre.equalsIgnoreCase("Children's Theatre")) {
            return EventType.FAMILY;
        }
        String segment = classification.segment() == null ? "" : String.valueOf(classification.segment().name());
        return switch (segment) {
            case "Sports" -> EventType.SPORTS;
            case "Music" -> EventType.MUSIC;
            case "Arts & Theatre", "Film" -> EventType.ARTS;
            default -> EventType.OTHER;
        };
    }

    /** Ticketmaster labels unknown genres "Undefined"; those say nothing. */
    private static String usable(String name) {
        return name == null || name.isBlank() || name.equalsIgnoreCase("Undefined") ? null : name;
    }

    /** A 16:9 image around 640px wide suits the cards; only https URLs are passed on. */
    private static String image(List<Image> images) {
        if (images == null) {
            return null;
        }
        return images.stream()
                .filter(i -> i.url() != null && i.url().startsWith("https://"))
                .min(Comparator.comparingInt((Image i) -> "16_9".equals(i.ratio()) ? 0 : 1)
                        .thenComparingInt(i -> Math.abs((i.width() == null ? 0 : i.width()) - 640)))
                .map(Image::url)
                .orElse(null);
    }

    private static String https(String url) {
        return url != null && url.startsWith("https://") ? url : null;
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Double number(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * 6371.0 * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
