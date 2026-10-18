package com.aitravel.places.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Request and response shapes for Google Routes API {@code POST /directions/v2:computeRoutes}; only fields we use. */
final class GoogleRoutesApi {

    private GoogleRoutesApi() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ComputeRoutesRequest(
            Waypoint origin,
            Waypoint destination,
            List<Waypoint> intermediates,
            String travelMode,
            String routingPreference,
            boolean optimizeWaypointOrder,
            String languageCode,
            String units) {
    }

    record Waypoint(Location location) {

        static Waypoint at(double latitude, double longitude) {
            return new Waypoint(new Location(new LatLng(latitude, longitude)));
        }
    }

    record Location(LatLng latLng) {
    }

    record LatLng(double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComputeRoutesResponse(List<Route> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Route(List<Integer> optimizedIntermediateWaypointIndex, List<Leg> legs) {
    }

    /** {@code duration} is a protobuf Duration string such as {@code "754s"}. Zero values are omitted by Google. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Leg(Long distanceMeters, String duration, Polyline polyline) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Polyline(String encodedPolyline) {
    }
}
