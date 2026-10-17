package com.aitravel.session.dto;

import com.aitravel.session.model.Interest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;

public record RecommendationRequest(
        @NotNull(message = "is required")
        @Valid
        LocationRequest location,

        @NotEmpty(message = "must not be empty")
        Set<@NotNull(message = "must not contain null values") Interest> interests,

        @NotNull(message = "is required")
        @DecimalMin(value = "0", message = "must not be negative")
        BigDecimal maxBudget,

        @NotBlank(message = "is required")
        @ValidCurrencyCode
        String currencyCode,

        @NotNull(message = "is required")
        @Min(value = 15, message = "must be between 15 and 720")
        @Max(value = 720, message = "must be between 15 and 720")
        Integer availableMinutes
) {
}
