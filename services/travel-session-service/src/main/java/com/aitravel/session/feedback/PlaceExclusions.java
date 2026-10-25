package com.aitravel.session.feedback;

import java.util.Set;

/** Places a traveler never wants suggested. Best effort: an unreachable store means no exclusions, not a failure. */
public interface PlaceExclusions {

    Set<String> excludedPlaceIds(String subject);

    static PlaceExclusions none() {
        return subject -> Set.of();
    }
}
