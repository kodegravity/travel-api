package com.aitravel.session.messaging;

import java.time.Instant;

/** A serialized event waiting to be published, stored next to the session change that caused it. */
public record OutboxEntry(String eventId, String eventType, String json, Instant createdAt) {
}
