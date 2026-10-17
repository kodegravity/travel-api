package com.aitravel.session.repository;

import com.aitravel.session.model.TravelSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Process-local session store. Restarting the application removes all sessions. Lookups are lock-free.
 * Capacity checks and purges take a short store-wide lock, held only for in-memory bookkeeping and never
 * during external calls.
 */
@Repository
public class InMemoryTravelSessionRepository implements TravelSessionRepository {

    private final ConcurrentHashMap<UUID, TravelSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<TravelSession> findById(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public synchronized boolean addIfCapacity(TravelSession session, int maxActive, Instant now,
                                              Duration retentionAfterExpiry) {
        purgeLocked(now, retentionAfterExpiry);
        long active = sessions.values().stream().filter(s -> s.isActiveAt(now)).count();
        if (active >= maxActive) {
            return false;
        }
        sessions.put(session.sessionId(), session);
        return true;
    }

    @Override
    public synchronized int purge(Instant now, Duration retentionAfterExpiry) {
        return purgeLocked(now, retentionAfterExpiry);
    }

    private int purgeLocked(Instant now, Duration retentionAfterExpiry) {
        int before = sessions.size();
        sessions.values().forEach(s -> s.expireIfDue(now));
        sessions.values().removeIf(s -> s.isPurgeableAt(now, retentionAfterExpiry));
        return before - sessions.size();
    }
}
