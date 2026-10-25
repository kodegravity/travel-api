package com.aitravel.session.feedback;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Process-local feedback store, the default ({@code travel.session.store=memory}). Lost on restart. */
@Repository
@ConditionalOnProperty(prefix = "travel.session", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryFeedbackStore implements FeedbackStore {

    private final Map<String, StoredFeedback> feedback = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredFeedback> find(String ownerKey) {
        return Optional.ofNullable(feedback.get(ownerKey));
    }

    @Override
    public synchronized boolean save(String ownerKey, long expectedVersion, StoredFeedback stored) {
        long current = find(ownerKey).map(StoredFeedback::version).orElse(0L);
        if (current != expectedVersion) {
            return false;
        }
        feedback.put(ownerKey, stored);
        return true;
    }
}
