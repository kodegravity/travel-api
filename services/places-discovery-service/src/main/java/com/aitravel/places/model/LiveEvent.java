package com.aitravel.places.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * A ticketed event happening near the traveler: a game, a concert, a show.
 *
 * @param startsAt   start instant; absent when the organiser has only announced a date
 * @param localDate  start date at the venue, as YYYY-MM-DD
 * @param localTime  start time at the venue, as HH:mm[:ss]; absent when not announced
 * @param distanceKm straight-line distance from the search point
 * @param source     "ticketmaster" or "sample"
 */
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
