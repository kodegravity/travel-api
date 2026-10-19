package com.aitravel.session.messaging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Single-task duplicate tracking for the in-memory store and tests; remembers the most recent 10,000 events. */
public class InMemoryProcessedEventStore implements ProcessedEventStore {

    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, Boolean> seen = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_ENTRIES;
        }
    });

    @Override
    public boolean markProcessed(String consumer, String eventId) {
        return seen.putIfAbsent(consumer + "#" + eventId, Boolean.TRUE) == null;
    }

    @Override
    public void release(String consumer, String eventId) {
        seen.remove(consumer + "#" + eventId);
    }
}
