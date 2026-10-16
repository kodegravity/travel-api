package com.aitravel.companion.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * An attraction returned by an {@code AttractionProvider}.
 *
 * <p>{@code estimatedCost} and {@code estimatedDurationMinutes} are estimates, not verified prices or
 * opening hours. Either may be {@code null} when unknown; callers must not treat unknown values as zero.
 */
public record Attraction(
        String id,
        String name,
        Set<Interest> categories,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal estimatedCost,
        String currencyCode,
        Integer estimatedDurationMinutes,
        String description
) {

    public Attraction {
        categories = Set.copyOf(categories);
    }

    /** True only when a cost is known and is expressed in the given currency, so it can be compared to a budget. */
    public boolean hasCostIn(String requestCurrencyCode) {
        return estimatedCost != null && requestCurrencyCode.equals(currencyCode);
    }
}
