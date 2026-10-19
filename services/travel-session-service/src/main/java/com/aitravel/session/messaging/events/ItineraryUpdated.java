package com.aitravel.session.messaging.events;

/**
 * Contract only, not produced yet: itineraries live in the browser today. Producing it needs itinerary sync first.
 */
public record ItineraryUpdated(String itineraryId, String cityId, int dayCount, int stopCount) {
}
