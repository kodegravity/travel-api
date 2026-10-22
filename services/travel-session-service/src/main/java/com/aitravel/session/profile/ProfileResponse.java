package com.aitravel.session.profile;

import com.aitravel.session.model.Interest;
import java.time.Instant;
import java.util.List;

/**
 * The signed-in traveler's profile.
 *
 * @param ownerRef  SHA-256 of the Okta subject, the same value the travel plan returns
 * @param interests sorted, so the response is stable
 */
public record ProfileResponse(
        String ownerRef,
        String displayName,
        String homeCity,
        List<Interest> interests,
        String currency,
        String travelMode,
        Instant createdAt,
        Instant updatedAt
) {

    static ProfileResponse of(String ownerRef, TravelerProfile profile) {
        return new ProfileResponse(ownerRef, profile.displayName(), profile.homeCity(),
                profile.interests().stream().sorted().toList(), profile.currency(), profile.travelMode(),
                profile.createdAt(), profile.updatedAt());
    }
}
