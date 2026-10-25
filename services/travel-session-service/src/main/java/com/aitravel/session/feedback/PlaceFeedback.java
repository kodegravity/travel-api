package com.aitravel.session.feedback;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * One rated place. Name, category, and city are copied from the card the traveler rated, so the list can be shown
 * without another places lookup.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceFeedback(
        String placeId,
        Rating rating,
        String name,
        String category,
        String cityId,
        Instant ratedAt
) {
}
