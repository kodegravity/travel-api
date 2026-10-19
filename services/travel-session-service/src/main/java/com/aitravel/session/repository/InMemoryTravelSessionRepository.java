package com.aitravel.session.repository;

import com.aitravel.session.messaging.OutboxEntry;
import com.aitravel.session.messaging.OutboxStore;
import com.aitravel.session.model.TravelSession;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Process-local session store, the default ({@code travel.session.store=memory}). Restarting the application
 * removes all sessions, and sessions are not shared between tasks. Lookups are lock-free. Capacity checks and
 * purges take a short store-wide lock, held only for in-memory bookkeeping and never during external calls.
 *
 * <p>The store hands out the live session objects, so {@link #save} has nothing to write and always succeeds.
 */
@Repository
@ConditionalOnProperty(prefix = "travel.session", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryTravelSessionRepository implements TravelSessionRepository, OutboxStore {

    private final ConcurrentHashMap<UUID, TravelSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, OutboxEntry> outbox = new ConcurrentHashMap<>();

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
    public boolean save(TravelSession session, Duration retentionAfterExpiry) {
        return sessions.get(session.sessionId()) == session;
    }

    @Override
    public synchronized boolean addIfCapacity(TravelSession session, int maxActive, Instant now,
                                              Duration retentionAfterExpiry, List<OutboxEntry> entries) {
        if (!addIfCapacity(session, maxActive, now, retentionAfterExpiry)) {
            return false;
        }
        addOutbox(entries);
        return true;
    }

    @Override
    public synchronized boolean save(TravelSession session, Duration retentionAfterExpiry, List<OutboxEntry> entries) {
        if (!save(session, retentionAfterExpiry)) {
            return false;
        }
        addOutbox(entries);
        return true;
    }

    @Override
    public void addOutbox(List<OutboxEntry> entries) {
        entries.forEach(entry -> outbox.put(entry.eventId(), entry));
    }

    @Override
    public List<OutboxEntry> pendingOutbox(Instant olderThan, int limit) {
        return outbox.values().stream()
                .filter(entry -> entry.createdAt().isBefore(olderThan))
                .sorted(Comparator.comparing(OutboxEntry::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public void removeOutbox(String eventId) {
        outbox.remove(eventId);
    }

    @Override
    public boolean tryBeginRefresh(TravelSession session, Instant now, Duration lease) {
        return session.tryBeginRefresh();
    }

    @Override
    public void endRefresh(TravelSession session) {
        session.endRefresh();
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
