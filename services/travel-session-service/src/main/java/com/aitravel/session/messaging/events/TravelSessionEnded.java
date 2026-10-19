package com.aitravel.session.messaging.events;

/** @param reason "STOPPED" by the traveler or "EXPIRED" by inactivity or lifetime */
public record TravelSessionEnded(String sessionId, String reason) {
}
