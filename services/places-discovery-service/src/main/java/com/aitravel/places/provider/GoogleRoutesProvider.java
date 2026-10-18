package com.aitravel.places.provider;

import com.aitravel.places.config.GooglePlacesProperties;
import com.aitravel.places.config.GoogleRoutesProperties;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.OptimizedRoute;
import com.aitravel.places.model.RouteRequest;
import com.aitravel.places.model.RouteRequest.Stop;
import com.aitravel.places.provider.GoogleRoutesApi.ComputeRoutesRequest;
import com.aitravel.places.provider.GoogleRoutesApi.ComputeRoutesResponse;
import com.aitravel.places.provider.GoogleRoutesApi.Waypoint;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Street routes from Google Routes API with {@code optimizeWaypointOrder}: one call per optimization.
 *
 * <p>Google optimizes only the stops between a fixed origin and destination. The trip has no fixed end, so the stop
 * farthest from the origin is used as the destination, which suits a day that moves outwards from the start.
 * Walking and driving only: Google does not optimize transit routes. Nothing is cached, per Google's terms.
 */
@Component
@ConditionalOnProperty(prefix = "attraction", name = "provider", havingValue = "google")
public class GoogleRoutesProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesProvider.class);
    static final String FIELD_MASK =
            "routes.optimizedIntermediateWaypointIndex,routes.legs.distanceMeters,routes.legs.duration,"
                    + "routes.legs.polyline.encodedPolyline";

    private final RestClient restClient;
    private final GooglePlacesProperties places;
    private final DailyCallLimiter dailyCallLimiter;

    @Autowired
    public GoogleRoutesProvider(@Qualifier("googleRoutesRestClient") RestClient restClient,
                                GooglePlacesProperties places, GoogleRoutesProperties routes) {
        this(restClient, places, routes, Clock.systemUTC());
    }

    GoogleRoutesProvider(RestClient restClient, GooglePlacesProperties places, GoogleRoutesProperties routes,
                         Clock clock) {
        this.restClient = restClient;
        this.places = places;
        this.dailyCallLimiter = new DailyCallLimiter("google-routes", routes.maxCallsPerDay(), clock);
    }

    @Override
    public OptimizedRoute optimize(RouteRequest request) {
        Stop destination = request.stops().stream()
                .max(Comparator.comparingDouble(stop -> request.origin().distanceKm(stop.point())))
                .orElseThrow();
        List<Stop> intermediates = request.stops().stream().filter(stop -> stop != destination).toList();

        if (!dailyCallLimiter.tryAcquire()) {
            log.warn("Google Routes daily call limit of {} reached, refusing optimization", dailyCallLimiter.maxCallsPerDay());
            throw new AttractionProviderException(
                    "Google Routes daily call limit of " + dailyCallLimiter.maxCallsPerDay() + " reached", null);
        }
        boolean drive = request.mode() == RouteRequest.TravelMode.DRIVE;
        ComputeRoutesRequest body = new ComputeRoutesRequest(
                Waypoint.at(request.origin().latitude(), request.origin().longitude()),
                Waypoint.at(destination.latitude(), destination.longitude()),
                intermediates.stream().map(stop -> Waypoint.at(stop.latitude(), stop.longitude())).toList(),
                drive ? "DRIVE" : "WALK",
                // Traffic-aware routing is billed higher; set only for driving, where Google accepts it.
                drive ? "TRAFFIC_UNAWARE" : null,
                true,
                "en",
                "METRIC");

        log.info("Calling Google Routes for {} stops, mode={}", request.stops().size(), request.mode());
        ComputeRoutesResponse response;
        try {
            response = restClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("X-Goog-Api-Key", places.apiKey())
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ComputeRoutesResponse.class);
        } catch (RestClientResponseException ex) {
            log.warn("Google Routes failed with HTTP status {}", ex.getStatusCode().value());
            throw new AttractionProviderException("Google Routes failed with HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("Google Routes could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Google Routes could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Google Routes response could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Google Routes response could not be read", ex);
        }
        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            log.warn("Google Routes found no route");
            throw new AttractionProviderException("Google Routes found no route", null);
        }
        return toRoute(response.routes().get(0), intermediates, destination);
    }

    private static OptimizedRoute toRoute(GoogleRoutesApi.Route route, List<Stop> intermediates, Stop destination) {
        List<Integer> visit = route.optimizedIntermediateWaypointIndex();
        if (visit == null || visit.size() != intermediates.size()) {
            visit = IntStream.range(0, intermediates.size()).boxed().toList();
        }
        List<String> order = new ArrayList<>();
        for (int index : visit) {
            if (index < 0 || index >= intermediates.size()) {
                throw new AttractionProviderException("Google Routes returned an invalid waypoint order", null);
            }
            order.add(intermediates.get(index).id());
        }
        order.add(destination.id());

        if (route.legs() == null || route.legs().size() != order.size()) {
            throw new AttractionProviderException("Google Routes returned an unexpected number of legs", null);
        }
        List<OptimizedRoute.Leg> legs = route.legs().stream()
                .map(leg -> new OptimizedRoute.Leg(
                        leg.distanceMeters() == null ? 0 : leg.distanceMeters(),
                        seconds(leg.duration()),
                        leg.polyline() == null ? null : leg.polyline().encodedPolyline()))
                .toList();
        return OptimizedRoute.of(order, legs, "google");
    }

    /** Parses a protobuf Duration such as {@code "754s"} or {@code "12.5s"}; missing means zero. */
    static long seconds(String duration) {
        if (duration == null || duration.isBlank()) {
            return 0;
        }
        try {
            return Math.round(Double.parseDouble(duration.endsWith("s") ? duration.substring(0, duration.length() - 1) : duration));
        } catch (NumberFormatException ex) {
            throw new AttractionProviderException("Google Routes returned an unreadable duration", ex);
        }
    }
}
