package com.aitravel.session.messaging;

/** Event types on the travel events topic, by their wire names. Consumers filter on these. */
public enum EventType {
    TRAVEL_SESSION_STARTED("TravelSessionStarted"),
    TRAVEL_SESSION_ENDED("TravelSessionEnded"),
    RECOMMENDATIONS_READY("RecommendationsReady"),
    ITINERARY_UPDATED("ItineraryUpdated"),
    NEARBY_EVENT_DISCOVERED("NearbyEventDiscovered");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static EventType fromWireName(String name) {
        for (EventType type : values()) {
            if (type.wireName.equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event type " + name);
    }
}
