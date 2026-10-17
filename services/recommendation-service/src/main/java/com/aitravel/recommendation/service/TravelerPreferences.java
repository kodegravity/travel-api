package com.aitravel.recommendation.service;

import com.aitravel.recommendation.model.Interest;
import java.math.BigDecimal;
import java.util.Set;

/** Traveler preferences passed to the AI engine. Budget applies only in {@code currencyCode}. */
public record TravelerPreferences(
        Set<Interest> interests,
        BigDecimal maxBudget,
        String currencyCode,
        int availableMinutes
) {
}
