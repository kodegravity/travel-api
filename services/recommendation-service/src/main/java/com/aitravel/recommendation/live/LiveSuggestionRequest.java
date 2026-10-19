package com.aitravel.recommendation.live;

import com.aitravel.recommendation.dto.LocationRequest;
import com.aitravel.recommendation.live.LiveEvent.EventType;
import com.aitravel.recommendation.model.Interest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * @param interests        the traveler's interests, used to choose among events
 * @param eventTypes       kinds of event the traveler wants to hear about
 * @param excludedEventIds events already suggested in this session
 */
public record LiveSuggestionRequest(
        @NotNull(message = "is required") @Valid LocationRequest location,
        Set<@NotNull Interest> interests,
        @NotEmpty(message = "must not be empty") Set<@NotNull EventType> eventTypes,
        @Size(max = 200, message = "must have at most 200 ids")
        Set<@NotNull @Pattern(regexp = "[A-Za-z0-9_.-]{1,128}", message = "is not a valid event id") String> excludedEventIds
) {

    public Set<Interest> interestsOrEmpty() {
        return interests == null ? Set.of() : interests;
    }

    public Set<String> excludedOrEmpty() {
        return excludedEventIds == null ? Set.of() : excludedEventIds;
    }
}
