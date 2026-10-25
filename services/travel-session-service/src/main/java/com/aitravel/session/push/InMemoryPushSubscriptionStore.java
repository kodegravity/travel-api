package com.aitravel.session.push;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Process-local subscription store, the default ({@code travel.session.store=memory}). Lost on restart. */
@Repository
@ConditionalOnProperty(prefix = "travel.session", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryPushSubscriptionStore implements PushSubscriptionStore {

    private final Map<String, StoredPushSubscriptions> subscriptions = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredPushSubscriptions> find(String ownerKey) {
        return Optional.ofNullable(subscriptions.get(ownerKey));
    }

    @Override
    public synchronized boolean save(String ownerKey, long expectedVersion, StoredPushSubscriptions stored) {
        long current = find(ownerKey).map(StoredPushSubscriptions::version).orElse(0L);
        if (current != expectedVersion) {
            return false;
        }
        subscriptions.put(ownerKey, stored);
        return true;
    }
}
