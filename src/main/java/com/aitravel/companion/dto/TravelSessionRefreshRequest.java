package com.aitravel.companion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TravelSessionRefreshRequest(
        @NotNull(message = "is required")
        @Valid
        LocationRequest location
) {
}
