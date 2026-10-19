package com.aitravel.session.messaging.events;

import java.time.Instant;
import java.util.List;

/** Live events newly suggested to a session. No venue coordinates: consumers do not need where the traveler is. */
public record NearbyEventDiscovered(String sessionId, List<Event> events) {

    public record Event(String eventId, String name, String type, String venueName, Instant startsAt, String headline) {
    }
}
