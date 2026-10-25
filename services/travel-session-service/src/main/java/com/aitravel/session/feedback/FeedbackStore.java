package com.aitravel.session.feedback;

import java.util.Optional;

/** Where place feedback lives, one document per traveler, keyed by a one-way hash of their Okta subject. */
public interface FeedbackStore {

    Optional<StoredFeedback> find(String ownerKey);

    /**
     * Stores {@code feedback} only if the stored version is still {@code expectedVersion} (0 when none is stored yet).
     *
     * @return false when another save got there first
     */
    boolean save(String ownerKey, long expectedVersion, StoredFeedback feedback);
}
