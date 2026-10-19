package com.aitravel.session.dto;

import com.aitravel.session.dto.LiveEventDto.EventType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * @param eventTypes   kinds of live event to alert about; absent or empty turns live alerts off
 * @param seenEventIds events this session has already shown, so they are not suggested again
 */
public record TravelSessionRefreshRequest(
        @NotNull(message = "is required")
        @Valid
        LocationRequest location,

        Set<@NotNull(message = "must not contain null values") EventType> eventTypes,

        @Size(max = 200, message = "must have at most 200 ids")
        Set<@NotNull(message = "must not contain null values")
            @Pattern(regexp = "[A-Za-z0-9_.-]{1,128}", message = "is not a valid event id") String> seenEventIds
) {

    public TravelSessionRefreshRequest(LocationRequest location) {
        this(location, null, null);
    }
}
