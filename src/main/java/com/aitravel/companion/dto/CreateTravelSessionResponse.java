package com.aitravel.companion.dto;

import com.aitravel.companion.model.TravelSessionStatus;
import java.time.Instant;
import java.util.UUID;

public record CreateTravelSessionResponse(UUID sessionId, TravelSessionStatus status, Instant expiresAt) {
}
