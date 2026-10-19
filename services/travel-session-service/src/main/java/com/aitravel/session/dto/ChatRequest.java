package com.aitravel.session.dto;

import com.aitravel.session.model.Interest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * One "Ask ROAM" chat turn: the recent conversation (oldest first, ending with the traveler) and their context.
 * Nothing is stored; the location, like every location, is never logged.
 */
public record ChatRequest(
        @NotEmpty(message = "must not be empty") @Size(max = 12, message = "must have at most 12 messages")
        List<@NotNull(message = "must not contain null values") @Valid Turn> messages,
        @NotNull(message = "is required") @Valid Context context
) {

    public enum Role { USER, ASSISTANT }

    public record Turn(
            @NotNull(message = "is required") Role role,
            @NotBlank(message = "must not be blank") @Size(max = 1000, message = "must be at most 1000 characters")
            String content
    ) {
    }

    public record Context(
            @Valid LocationRequest location,
            @Size(max = 80, message = "must be at most 80 characters") String placeName,
            @NotNull(message = "is required")
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?", message = "must be a local date-time like 2026-10-19T14:30")
            String localDateTime,
            Set<@NotNull(message = "must not contain null values") Interest> interests,
            @DecimalMin(value = "0", message = "must not be negative") BigDecimal maxBudget,
            @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO currency code") String currencyCode,
            @Min(value = 15, message = "must be between 15 and 720") @Max(value = 720, message = "must be between 15 and 720")
            Integer availableMinutes,
            @Size(max = 25, message = "must have at most 25 stops")
            List<@NotBlank(message = "must not be blank") @Size(max = 120, message = "must be at most 120 characters") String> tripStops
    ) {
    }

    @AssertTrue(message = "the last message must be from the traveler")
    public boolean isLastMessageFromUser() {
        return messages == null || messages.isEmpty() || messages.get(messages.size() - 1) == null
                || messages.get(messages.size() - 1).role() == Role.USER;
    }
}
