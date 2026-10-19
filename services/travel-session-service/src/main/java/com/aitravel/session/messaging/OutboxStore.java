package com.aitravel.session.messaging;

import java.time.Instant;
import java.util.List;

/**
 * Where outbox entries wait. Writes that go with a session change happen in the repository's own transaction; these
 * methods cover events with no session change, and the relay's bookkeeping.
 */
public interface OutboxStore {

    /** Stores entries that are not tied to a session write. */
    void addOutbox(List<OutboxEntry> entries);

    /** Entries created before {@code olderThan}, oldest first, at most {@code limit}. */
    List<OutboxEntry> pendingOutbox(Instant olderThan, int limit);

    void removeOutbox(String eventId);
}
