package com.aitravel.places.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A trip to put in the best visiting order: start at {@code origin}, visit every stop once, end at the last stop.
 *
 * @param mode   how the traveller moves between stops
 * @param origin where the day starts
 * @param stops  places to visit, 2 to 25 (Google allows at most 25 waypoints besides the origin)
 */
public record RouteRequest(
        @NotNull TravelMode mode,
        @NotNull @Valid Point origin,
        @NotNull @Size(min = 2, max = 25) List<@NotNull @Valid Stop> stops
) {

    public enum TravelMode { WALK, DRIVE }

    public record Point(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude
    ) {

        /** Great-circle distance in kilometres. */
        public double distanceKm(Point other) {
            double dLat = Math.toRadians(other.latitude - latitude);
            double dLng = Math.toRadians(other.longitude - longitude);
            double h = Math.pow(Math.sin(dLat / 2), 2)
                    + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                    * Math.pow(Math.sin(dLng / 2), 2);
            return 2 * 6371.0 * Math.asin(Math.min(1, Math.sqrt(h)));
        }
    }

    public record Stop(
            @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,256}", message = "is not a valid place id") String id,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude
    ) {

        public Point point() {
            return new Point(latitude, longitude);
        }
    }
}
