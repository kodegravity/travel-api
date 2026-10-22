package com.aitravel.session.profile;

import com.aitravel.session.dto.ValidCurrencyCode;
import com.aitravel.session.model.Interest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Replaces the whole profile. Blank text clears a field.
 *
 * @param displayName up to 60 characters, no control characters
 * @param homeCity    up to 80 characters, no control characters
 * @param interests   any of the interests, possibly none
 * @param currency    ISO 4217 code, or blank for none
 * @param travelMode  walk, transit, or drive
 */
public record UpdateProfileRequest(
        @Size(max = 60) @Pattern(regexp = "[^\\p{Cntrl}]*") String displayName,
        @Size(max = 80) @Pattern(regexp = "[^\\p{Cntrl}]*") String homeCity,
        @NotNull Set<Interest> interests,
        @ValidCurrencyCode String currency,
        @NotNull @Pattern(regexp = "walk|transit|drive") String travelMode
) {
}
