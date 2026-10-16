package com.aitravel.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.aitravel.companion.config.RecommendationProperties;
import com.aitravel.companion.dto.LocationRequest;
import com.aitravel.companion.dto.RecommendationRequest;
import com.aitravel.companion.model.Attraction;
import com.aitravel.companion.model.Interest;
import com.aitravel.companion.service.RecommendationRankingService.ScoreBreakdown;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationRankingServiceTest {

    private static final RecommendationProperties PROPERTIES = new RecommendationProperties(5.0, 4.5, 3);
    private final RecommendationRankingService ranking = new RecommendationRankingService(PROPERTIES);

    @Test
    void closerAttractionScoresHigherDistanceComponent() {
        RecommendationRequest request = request(Set.of(Interest.HISTORY), "50", "EUR", 180);

        ScoreBreakdown near = ranking.score(candidate(attraction(Set.of(Interest.HISTORY), "16", 60), 0.5), request);
        ScoreBreakdown far = ranking.score(candidate(attraction(Set.of(Interest.HISTORY), "16", 60), 4.0), request);

        assertThat(near.distanceScore()).isGreaterThan(far.distanceScore());
        assertThat(near.totalScore()).isGreaterThan(far.totalScore());
    }

    @Test
    void distanceScoreIsZeroAtSearchRadius() {
        assertThat(ranking.distanceScore(5.0)).isZero();
        assertThat(ranking.distanceScore(0.0)).isEqualTo(1.0);
    }

    @Test
    void fullInterestMatchScoresOneAndPartialMatchScoresFraction() {
        Set<Interest> categories = Set.of(Interest.HISTORY);
        Set<Interest> requested = Set.of(Interest.HISTORY, Interest.FOOD);

        assertThat(ranking.interestScore(categories, Set.of(Interest.HISTORY))).isEqualTo(1.0);
        assertThat(ranking.interestScore(categories, requested)).isEqualTo(0.5);
        assertThat(ranking.interestScore(categories, Set.of(Interest.ART))).isZero();
    }

    @Test
    void cheaperAttractionScoresHigherBudgetComponent() {
        RecommendationRequest request = request(Set.of(Interest.HISTORY), "50", "EUR", 180);

        ScoreBreakdown cheap = ranking.score(candidate(attraction(Set.of(Interest.HISTORY), "5", 60), 1.0), request);
        ScoreBreakdown pricey = ranking.score(candidate(attraction(Set.of(Interest.HISTORY), "45", 60), 1.0), request);

        assertThat(cheap.budgetScore()).isGreaterThan(pricey.budgetScore());
    }

    @Test
    void unknownCostOrCurrencyMismatchGivesNeutralBudgetScore() {
        RecommendationRequest request = request(Set.of(Interest.HISTORY), "50", "EUR", 180);
        Attraction unknownCost = new Attraction("a", "A", Set.of(Interest.HISTORY),
                new BigDecimal("41.9"), new BigDecimal("12.5"), null, null, 60, "d");
        Attraction otherCurrency = new Attraction("b", "B", Set.of(Interest.HISTORY),
                new BigDecimal("41.9"), new BigDecimal("12.5"), new BigDecimal("500"), "USD", 60, "d");

        assertThat(ranking.budgetScore(unknownCost, request)).isEqualTo(RecommendationRankingService.NEUTRAL_SCORE);
        assertThat(ranking.budgetScore(otherCurrency, request)).isEqualTo(RecommendationRankingService.NEUTRAL_SCORE);
    }

    @Test
    void moreSpareTimeScoresHigherTimeComponent() {
        RecommendationRequest request = request(Set.of(Interest.HISTORY), "50", "EUR", 180);

        ScoreBreakdown roomy = ranking.score(candidate(attraction(Set.of(Interest.HISTORY), "5", 30), 0.5), request);
        ScoreBreakdown tight = ranking.score(candidate(attraction(Set.of(Interest.HISTORY), "5", 150), 0.5), request);

        assertThat(roomy.timeScore()).isGreaterThan(tight.timeScore());
    }

    @Test
    void totalScoreIsWeightedSumWithinUnitInterval() {
        RecommendationRequest request = request(Set.of(Interest.HISTORY), "50", "EUR", 180);

        ScoreBreakdown best = ranking.score(candidate(attraction(Set.of(Interest.HISTORY), "0", 0), 0.0), request);
        ScoreBreakdown worst = ranking.score(candidate(attraction(Set.of(Interest.ART), "50", 180), 5.0), request);

        assertThat(best.totalScore()).isEqualTo(1.0, within(1e-9));
        assertThat(worst.totalScore()).isBetween(0.0, 1.0);
        assertThat(worst.totalScore()).isEqualTo(
                0.40 * worst.interestScore() + 0.25 * worst.distanceScore()
                        + 0.20 * worst.budgetScore() + 0.15 * worst.timeScore(), within(1e-9));
    }

    @Test
    void weightsSumToOne() {
        double sum = RecommendationRankingService.INTEREST_WEIGHT + RecommendationRankingService.DISTANCE_WEIGHT
                + RecommendationRankingService.BUDGET_WEIGHT + RecommendationRankingService.TIME_WEIGHT;

        assertThat(sum).isEqualTo(1.0, within(1e-9));
    }

    private static RecommendationRequest request(Set<Interest> interests, String budget, String currency, int minutes) {
        return new RecommendationRequest(
                new LocationRequest(new BigDecimal("41.8902"), new BigDecimal("12.4922")),
                interests, new BigDecimal(budget), currency, minutes);
    }

    private static Attraction attraction(Set<Interest> categories, String cost, int durationMinutes) {
        return new Attraction("id-" + cost + "-" + durationMinutes, "Name", categories,
                new BigDecimal("41.9"), new BigDecimal("12.5"),
                new BigDecimal(cost), "EUR", durationMinutes, "description");
    }

    private static CandidateAttraction candidate(Attraction attraction, double distanceKm) {
        return new CandidateAttraction(attraction, distanceKm, distanceKm / 4.5 * 60.0);
    }
}
