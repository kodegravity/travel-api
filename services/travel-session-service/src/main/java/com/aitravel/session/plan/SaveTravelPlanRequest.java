package com.aitravel.session.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Replaces the whole plan, but only if it is still at {@code baseVersion}; otherwise the save is rejected with 409
 * and the web app merges with the newer plan and tries again.
 */
public record SaveTravelPlanRequest(
        @NotNull @PositiveOrZero Long baseVersion,
        @NotNull @Size(max = MAX_ENTRIES, message = "must have at most " + MAX_ENTRIES + " entries")
        Map<@NotNull @Pattern(regexp = KEY_PATTERN, message = "must be 'favorites' or 'city:<cityId>'") String,
                @NotNull @Valid TravelPlanEntry> entries
) {
    static final int MAX_ENTRIES = 200;
    static final String KEY_PATTERN = "favorites|city:[A-Za-z0-9_-]{1,64}";
}
