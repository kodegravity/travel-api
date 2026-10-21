package com.aitravel.session.messaging.events;

/**
 * A traveler's synced itinerary for a city was created or changed. {@code itineraryId} is the owner's one-way hash
 * and the city, never the identity itself.
 */
public record ItineraryUpdated(String itineraryId, String cityId, int dayCount, int stopCount) {
}
