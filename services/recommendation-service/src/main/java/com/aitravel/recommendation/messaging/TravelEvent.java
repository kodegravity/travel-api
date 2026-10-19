package com.aitravel.recommendation.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** The travel events envelope, as travel-session publishes it (see docs/messaging.md). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TravelEvent(String eventId, String eventType, int eventVersion, Instant timestamp, String correlationId,
                          JsonNode payload) {
}
