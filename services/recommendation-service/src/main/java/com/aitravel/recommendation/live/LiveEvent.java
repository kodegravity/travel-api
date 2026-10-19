package com.aitravel.recommendation.live;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** A ticketed event near the traveler, as Places Discovery reports it. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveEvent(
        String id,
        String name,
        EventType type,
        String genre,
        String venueName,
        Double latitude,
        Double longitude,
        Instant startsAt,
        String localDate,
        String localTime,
        Double distanceKm,
        String url,
        String imageUrl,
        String source
) {

    public enum EventType { SPORTS, MUSIC, ARTS, FAMILY, OTHER }
}
