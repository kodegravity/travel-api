package com.aitravel.session.repository;

import com.aitravel.session.model.TravelSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TravelSessionRepository {

    Optional<TravelSession> findById(UUID sessionId);

    /**
     * Stores the session only if fewer than {@code maxActive} sessions are active after expired ones are
     * expired and purged. Returns false when the store is full.
     */
    boolean addIfCapacity(TravelSession session, int maxActive, Instant now, Duration retentionAfterExpiry);

    /** Expires due sessions and removes those past the retention window. Returns the number removed. */
    int purge(Instant now, Duration retentionAfterExpiry);
}
