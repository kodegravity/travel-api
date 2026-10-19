package com.aitravel.session.repository;

import com.aitravel.session.messaging.OutboxEntry;
import com.aitravel.session.model.TravelSession;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Session store. Implementations either share live objects (in-memory) or hand out copies (DynamoDB). Callers must
 * therefore {@link #save} after every change, and treat a {@code false} result as "someone else changed this
 * session first": re-read it and decide again.
 */
public interface TravelSessionRepository {

    Optional<TravelSession> findById(UUID sessionId);

    /**
     * Stores the session only if fewer than {@code maxActive} sessions are active after expired ones are
     * expired and purged. Returns false when the store is full.
     */
    boolean addIfCapacity(TravelSession session, int maxActive, Instant now, Duration retentionAfterExpiry);

    /** {@link #addIfCapacity}, also writing {@code outbox} entries atomically with the new session. */
    default boolean addIfCapacity(TravelSession session, int maxActive, Instant now, Duration retentionAfterExpiry,
                                  List<OutboxEntry> outbox) {
        if (!outbox.isEmpty()) {
            throw new UnsupportedOperationException("This store does not support an outbox");
        }
        return addIfCapacity(session, maxActive, now, retentionAfterExpiry);
    }

    /**
     * Persists the session if its stored version still equals {@link TravelSession#version()}, then advances the
     * version. Returns false, writing nothing, when another writer saved first or the session no longer exists.
     */
    boolean save(TravelSession session, Duration retentionAfterExpiry);

    /** {@link #save}, also writing {@code outbox} entries atomically with the change: both or neither. */
    default boolean save(TravelSession session, Duration retentionAfterExpiry, List<OutboxEntry> outbox) {
        if (!outbox.isEmpty()) {
            throw new UnsupportedOperationException("This store does not support an outbox");
        }
        return save(session, retentionAfterExpiry);
    }

    /**
     * Takes the per-session refresh lease so only one refresh runs at a time, across every task. The lease ends
     * with {@link #endRefresh} or, if the holder dies, after {@code lease}. Returns false while another holder has it.
     */
    boolean tryBeginRefresh(TravelSession session, Instant now, Duration lease);

    void endRefresh(TravelSession session);

    /** Expires due sessions and removes those past the retention window. Returns the number removed. */
    int purge(Instant now, Duration retentionAfterExpiry);
}
