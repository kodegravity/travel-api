package com.aitravel.session.push;

import java.util.Optional;

/** Where push subscriptions live, one document per traveler, keyed by a one-way hash of their Okta subject. */
public interface PushSubscriptionStore {

    Optional<StoredPushSubscriptions> find(String ownerKey);

    /**
     * Stores {@code subscriptions} only if the stored version is still {@code expectedVersion} (0 when none is stored
     * yet).
     *
     * @return false when another save got there first
     */
    boolean save(String ownerKey, long expectedVersion, StoredPushSubscriptions subscriptions);
}
