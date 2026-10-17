package com.aitravel.places.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /internal/v1/places/search}.
 *
 * @param location       search centre
 * @param searchRadiusKm maximum distance from the centre in kilometres. 50 km is the Google Places
 *                       {@code searchNearby} circle limit (50,000 m).
 */
public record PlacesSearchRequest(
        @NotNull @Valid Coordinates location,
        @NotNull @DecimalMin("0.1") @DecimalMax("50.0") Double searchRadiusKm
) {

    public record Coordinates(
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
    ) {
    }
}
