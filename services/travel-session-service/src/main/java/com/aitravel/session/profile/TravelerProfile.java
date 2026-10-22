package com.aitravel.session.profile;

import com.aitravel.session.model.Interest;
import java.time.Instant;
import java.util.Set;

/**
 * A traveler's profile and preferences as stored. Identity and credentials stay in Okta.
 *
 * @param displayName how the app greets the traveler; null when not set
 * @param homeCity    free text, for example "Toronto"; null when not set
 * @param interests   default interests for new searches
 * @param currency    ISO 4217 code for budgets; null when not set
 * @param travelMode  default for routes and itineraries: walk, transit, or drive
 * @param createdAt   when the traveler was first seen
 * @param updatedAt   when the traveler last saved the profile
 */
public record TravelerProfile(
        String displayName,
        String homeCity,
        Set<Interest> interests,
        String currency,
        String travelMode,
        Instant createdAt,
        Instant updatedAt
) {

    static final String DEFAULT_TRAVEL_MODE = "walk";

    public TravelerProfile {
        interests = Set.copyOf(interests);
    }

    /** A traveler seen for the first time. */
    static TravelerProfile newTraveler(Instant now) {
        return new TravelerProfile(null, null, Set.of(), null, DEFAULT_TRAVEL_MODE, now, now);
    }
}
