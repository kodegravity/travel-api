package com.aitravel.session.plan;

import java.util.Optional;

/** Where travel plans live, one per traveler, keyed by a one-way hash of their Okta subject. */
public interface TravelPlanStore {

    Optional<StoredTravelPlan> find(String ownerKey);

    /**
     * Stores {@code plan} only if the stored version is still {@code expectedVersion} (0 when none is stored yet).
     *
     * @return false when another save got there first
     */
    boolean save(String ownerKey, long expectedVersion, StoredTravelPlan plan);
}
