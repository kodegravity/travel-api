package com.aitravel.session.dto;

import com.aitravel.session.model.Interest;
import java.util.Set;

/** Sent to the Recommendation Service: where the traveler is, what they like, and what they have already seen. */
public record LiveSuggestionRequest(
        LocationRequest location,
        Set<Interest> interests,
        Set<LiveEventDto.EventType> eventTypes,
        Set<String> excludedEventIds
) {
}
