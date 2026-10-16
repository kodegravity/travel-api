package com.aitravel.companion.service;

import com.aitravel.companion.config.RecommendationProperties;
import com.aitravel.companion.dto.RecommendationRequest;
import com.aitravel.companion.model.Attraction;
import com.aitravel.companion.model.Interest;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic, explainable scoring of eligible attractions. No AI or ML is used.
 *
 * <p>Each component is normalised to [0, 1]; the total is the weighted sum below (weights sum to 1.0):
 * <ul>
 *   <li><b>Interest (40%)</b>: fraction of the traveler's selected interests the attraction covers.</li>
 *   <li><b>Distance (25%)</b>: {@code 1 - distance / searchRadius}. Closer is higher.</li>
 *   <li><b>Budget (20%)</b>: {@code 1 - cost / maxBudget}. Cheaper is higher. Unknown cost or a
 *       currency mismatch gives a neutral {@value #NEUTRAL_SCORE}, because the cost cannot be compared.</li>
 *   <li><b>Time (15%)</b>: spare minutes ({@code available - walking - visit}) as a fraction of
 *       {@code available}. More slack is higher.</li>
 * </ul>
 *
 * <p>This class is the single place where the scoring formula is defined.
 */
@Component
public class RecommendationRankingService {

    static final double INTEREST_WEIGHT = 0.40;
    static final double DISTANCE_WEIGHT = 0.25;
    static final double BUDGET_WEIGHT = 0.20;
    static final double TIME_WEIGHT = 0.15;
    static final double NEUTRAL_SCORE = 0.5;

    private final RecommendationProperties properties;

    public RecommendationRankingService(RecommendationProperties properties) {
        this.properties = properties;
    }

    public ScoreBreakdown score(CandidateAttraction candidate, RecommendationRequest request) {
        Attraction attraction = candidate.attraction();
        double interest = interestScore(attraction.categories(), request.interests());
        double distance = distanceScore(candidate.distanceKm());
        double budget = budgetScore(attraction, request);
        double time = timeScore(candidate, request.availableMinutes());
        double total = INTEREST_WEIGHT * interest
                + DISTANCE_WEIGHT * distance
                + BUDGET_WEIGHT * budget
                + TIME_WEIGHT * time;
        return new ScoreBreakdown(interest, distance, budget, time, clamp(total));
    }

    double interestScore(Set<Interest> categories, Set<Interest> requested) {
        if (requested.isEmpty()) {
            return 0.0;
        }
        long matches = requested.stream().filter(categories::contains).count();
        return (double) matches / requested.size();
    }

    double distanceScore(double distanceKm) {
        return clamp(1.0 - distanceKm / properties.searchRadiusKm());
    }

    double budgetScore(Attraction attraction, RecommendationRequest request) {
        if (!attraction.hasCostIn(request.currencyCode())) {
            return NEUTRAL_SCORE;
        }
        BigDecimal cost = attraction.estimatedCost();
        BigDecimal budget = request.maxBudget();
        if (budget.signum() == 0) {
            // Cost cannot exceed a zero budget here, so it is zero and fits exactly.
            return 1.0;
        }
        return clamp(1.0 - cost.doubleValue() / budget.doubleValue());
    }

    double timeScore(CandidateAttraction candidate, int availableMinutes) {
        double used = candidate.walkingMinutes() + candidate.attraction().estimatedDurationMinutes();
        return clamp((availableMinutes - used) / availableMinutes);
    }

    static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Per-component scores and the weighted total, each in [0, 1]. */
    public record ScoreBreakdown(
            double interestScore,
            double distanceScore,
            double budgetScore,
            double timeScore,
            double totalScore
    ) {
    }
}
