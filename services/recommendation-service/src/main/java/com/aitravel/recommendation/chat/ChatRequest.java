package com.aitravel.recommendation.chat;

import com.aitravel.recommendation.dto.LocationRequest;
import com.aitravel.recommendation.model.Interest;
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
 * One chat turn: the recent conversation (oldest first, ending with the traveler's message) and what the app knows
 * about the traveler right now. Nothing is stored; the app sends the history each time.
 */
public record ChatRequest(
        @NotEmpty(message = "must not be empty") @Size(max = 12, message = "must have at most 12 messages")
        List<@NotNull @Valid Turn> messages,
        @NotNull(message = "is required") @Valid Context context
) {

    public enum Role { USER, ASSISTANT }

    public record Turn(
            @NotNull(message = "is required") Role role,
            @NotBlank(message = "must not be blank") @Size(max = 1000, message = "must be at most 1000 characters")
            String content
    ) {
    }

    /**
     * @param location         where the traveler is; absent when they have not shared it
     * @param placeName        the city or area name to talk about, such as "Madrid"
     * @param localDateTime    the traveler's local date and time, such as 2026-10-19T14:30
     * @param maxBudget        what they want to spend, in {@code currencyCode}
     * @param availableMinutes how long they have
     * @param tripStops        names of places already in their trip, in order
     */
    public record Context(
            @Valid LocationRequest location,
            @Size(max = 80) String placeName,
            @NotNull(message = "is required")
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?", message = "must be a local date-time like 2026-10-19T14:30")
            String localDateTime,
            Set<@NotNull Interest> interests,
            @DecimalMin(value = "0", message = "must not be negative") BigDecimal maxBudget,
            @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO currency code") String currencyCode,
            @Min(15) @Max(720) Integer availableMinutes,
            @Size(max = 25) List<@NotBlank @Size(max = 120) String> tripStops
    ) {

        public Set<Interest> interestsOrEmpty() {
            return interests == null ? Set.of() : interests;
        }

        public List<String> tripStopsOrEmpty() {
            return tripStops == null ? List.of() : tripStops;
        }
    }

    @AssertTrue(message = "the last message must be from the traveler")
    public boolean isLastMessageFromUser() {
        return messages == null || messages.isEmpty() || messages.get(messages.size() - 1) == null
                || messages.get(messages.size() - 1).role() == Role.USER;
    }
}
