package com.aitravel.session.plan;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Process-local plan store, the default ({@code travel.session.store=memory}). Lost on restart. */
@Repository
@ConditionalOnProperty(prefix = "travel.session", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryTravelPlanStore implements TravelPlanStore {

    private final Map<String, StoredTravelPlan> plans = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredTravelPlan> find(String ownerKey) {
        return Optional.ofNullable(plans.get(ownerKey));
    }

    @Override
    public synchronized boolean save(String ownerKey, long expectedVersion, StoredTravelPlan plan) {
        long current = find(ownerKey).map(StoredTravelPlan::version).orElse(0L);
        if (current != expectedVersion) {
            return false;
        }
        plans.put(ownerKey, plan);
        return true;
    }
}
