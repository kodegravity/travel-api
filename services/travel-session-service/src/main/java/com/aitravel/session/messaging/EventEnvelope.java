package com.aitravel.session.messaging;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * Every message on the travel events topic. {@code eventId} makes processing idempotent; {@code correlationId} is
 * the request ID of the HTTP request that caused the event, so logs line up across services. Payloads never carry
 * coordinates or raw user identifiers.
 *
 * @param eventVersion version of this event type's payload; bump it on a breaking change
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int eventVersion,
        Instant timestamp,
        String correlationId,
        JsonNode payload
) {
}
