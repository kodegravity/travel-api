package com.aitravel.session.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TravelSessionRecommendationResponse(
        UUID sessionId,
        RefreshStatus refreshStatus,
        List<RecommendationDto> recommendations,
        Instant generatedAt,
        /** Live events worth an alert; empty when alerts are off, nothing new is on, or the event source failed. */
        List<LiveSuggestionDto> liveSuggestions
) {
}
