package com.aitravel.session.dto;

import com.aitravel.session.model.TravelSessionStatus;
import java.time.Instant;
import java.util.UUID;

public record CreateTravelSessionResponse(UUID sessionId, TravelSessionStatus status, Instant expiresAt) {
}
