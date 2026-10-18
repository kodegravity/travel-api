package com.aitravel.places.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The stops in visiting order and one leg per stop: {@code legs[0]} runs from the origin to {@code order[0]},
 * {@code legs[i]} from {@code order[i-1]} to {@code order[i]}.
 *
 * @param source "google" for Google Routes travel times, "estimate" for distance-based estimates
 */
public record OptimizedRoute(
        List<String> order,
        List<Leg> legs,
        long distanceMeters,
        long durationSeconds,
        String source
) {

    /** @param polyline Google encoded polyline of the street route; absent for estimates */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Leg(long distanceMeters, long durationSeconds, String polyline) {
    }

    public static OptimizedRoute of(List<String> order, List<Leg> legs, String source) {
        return new OptimizedRoute(order, legs,
                legs.stream().mapToLong(Leg::distanceMeters).sum(),
                legs.stream().mapToLong(Leg::durationSeconds).sum(),
                source);
    }
}
