package com.aitravel.session.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * A trip to put in the best visiting order: start at {@code origin}, visit every stop once. The origin may be the
 * traveller's own position, so like every location it is never logged.
 */
public record RouteOptimizeRequest(
        @NotNull(message = "is required") Mode mode,
        @NotNull(message = "is required") @Valid LocationRequest origin,
        @NotNull(message = "is required")
        @Size(min = 2, max = 25, message = "must have between 2 and 25 stops")
        List<@NotNull(message = "must not contain null values") @Valid Stop> stops
) {

    /** Google optimizes walking and driving routes only; the app estimates transit itself. */
    public enum Mode { WALK, DRIVE }

    public record Stop(
            @NotNull(message = "is required")
            @Pattern(regexp = "[A-Za-z0-9_-]{1,256}", message = "is not a valid place id")
            String id,

            @NotNull(message = "is required")
            @DecimalMin(value = "-90", message = "must be between -90 and 90")
            @DecimalMax(value = "90", message = "must be between -90 and 90")
            BigDecimal latitude,

            @NotNull(message = "is required")
            @DecimalMin(value = "-180", message = "must be between -180 and 180")
            @DecimalMax(value = "180", message = "must be between -180 and 180")
            BigDecimal longitude
    ) {
    }
}
