package com.aitravel.session.plan;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * One synced part of a traveler's plan: a city's trip and itinerary, or their favorites. The web app owns the shape
 * of {@code data}; the service stores it as given. A null {@code data} records a deletion, so it can win a merge.
 *
 * @param updatedAt when the traveler last changed this part, on their device; the newer side wins a merge
 * @param data      the part itself, or null when deleted
 */
public record TravelPlanEntry(@NotNull Instant updatedAt, JsonNode data) {

    boolean deleted() {
        return data == null || data.isNull();
    }
}
