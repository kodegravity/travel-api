package com.aitravel.session.feedback;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Rates one place. The text fields are shown back in the traveler's feedback list.
 *
 * @param rating   like or dislike
 * @param name     the place name as shown on the card
 * @param category the card's category, if any
 * @param cityId   the city the place was rated in, if any
 */
public record RatePlaceRequest(
        @NotNull Rating rating,
        @Size(max = 120) @Pattern(regexp = "[^\\p{Cntrl}]*") String name,
        @Size(max = 60) @Pattern(regexp = "[^\\p{Cntrl}]*") String category,
        @Size(max = 60) @Pattern(regexp = "[a-z0-9-]*") String cityId
) {
}
