package com.aitravel.session.profile;

import com.aitravel.session.model.Interest;
import java.time.Instant;
import java.util.Set;

/** Where traveler profiles live, keyed by a one-way hash of the Okta subject. */
public interface ProfileStore {

    /** Returns the profile, creating it the first time the traveler is seen, and records {@code now} as last seen. */
    TravelerProfile touch(String ownerKey, Instant now);

    /** Replaces the profile's fields, creating it if needed, and returns what was stored. */
    TravelerProfile save(String ownerKey, ProfileFields fields, Instant now);

    /** The editable part of a profile, already trimmed (blank text is null). */
    record ProfileFields(String displayName, String homeCity, Set<Interest> interests, String currency,
                         String travelMode) {

        public ProfileFields {
            interests = Set.copyOf(interests);
        }
    }
}
