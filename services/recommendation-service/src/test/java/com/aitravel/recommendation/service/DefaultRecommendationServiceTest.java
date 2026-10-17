package com.aitravel.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.aitravel.recommendation.config.RecommendationProperties;
import com.aitravel.recommendation.dto.LocationRequest;
import com.aitravel.recommendation.dto.RecommendationDto;
import com.aitravel.recommendation.dto.RecommendationRequest;
import com.aitravel.recommendation.dto.RecommendationResponse;
import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import com.aitravel.recommendation.provider.AttractionProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DefaultRecommendationServiceTest {

    // Traveler at the Colosseum. 1 degree of latitude is about 111.2 km, so 0.0045 is about 0.5 km.
    private static final String ORIGIN_LAT = "41.8902";
    private static final String ORIGIN_LON = "12.4922";
    private static final String NORTH_0_5_KM = "41.8947";
    private static final String NORTH_4_9_KM = "41.9342";
    private static final String NORTH_6_1_KM = "41.9452";

    @Mock
    private AttractionProvider attractionProvider;

    private DefaultRecommendationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        RecommendationProperties properties = new RecommendationProperties(5.0, 4.5, 3);
        service = new DefaultRecommendationService(
                attractionProvider, new RecommendationRankingService(properties), properties, Optional.empty());
    }

    private DefaultRecommendationService serviceWithAi(AIRecommendationService ai) {
        RecommendationProperties properties = new RecommendationProperties(5.0, 4.5, 3);
        return new DefaultRecommendationService(
                attractionProvider, new RecommendationRankingService(properties), properties, Optional.of(ai));
    }

    @Test
    void aiEngineReceivesOnlyEligibleCandidatesAndItsSelectionsAndReasonsAreReturned() {
        givenProvider(
                attraction("history", Set.of(Interest.HISTORY), "16", "EUR", 60, ORIGIN_LAT, ORIGIN_LON),
                attraction("food", Set.of(Interest.FOOD), "10", "EUR", 60, ORIGIN_LAT, ORIGIN_LON),
                attraction("pricey", Set.of(Interest.HISTORY), "99", "EUR", 60, ORIGIN_LAT, ORIGIN_LON));
        List<String> offered = new java.util.ArrayList<>();
        AIRecommendationService ai = (preferences, candidates) -> {
            candidates.forEach(c -> offered.add(c.id()));
            return List.of(
                    new AiRecommendation("history", "Fits your history interest."),
                    new AiRecommendation("not-a-candidate", "Invented place."));
        };

        RecommendationResponse response = serviceWithAi(ai).recommend(
                request(Set.of(Interest.HISTORY), "50", "EUR", 180));

        assertThat(offered).containsExactly("history");
        assertThat(response.recommendations()).extracting(RecommendationDto::placeId).containsExactly("history");
        assertThat(response.recommendations().get(0).description()).isEqualTo("Fits your history interest.");
    }

    @Test
    void aiEngineIsNotCalledWhenNoCandidateIsEligible() {
        givenProvider(
                attraction("pricey", Set.of(Interest.HISTORY), "99", "EUR", 60, ORIGIN_LAT, ORIGIN_LON));
        AIRecommendationService ai = (preferences, candidates) -> {
            throw new AssertionError("AI must not be called without eligible candidates");
        };

        RecommendationResponse response = serviceWithAi(ai).recommend(
                request(Set.of(Interest.HISTORY), "50", "EUR", 180));

        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void returnsOnlyAttractionsMatchingSelectedInterests() {
        givenProvider(
                attraction("history", Set.of(Interest.HISTORY), "16", "EUR", 60, ORIGIN_LAT, ORIGIN_LON),
                attraction("food", Set.of(Interest.FOOD), "10", "EUR", 60, ORIGIN_LAT, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.HISTORY), "50", "EUR", 180));

        assertThat(ids(results)).containsExactly("history");
    }

    @Test
    void excludesAttractionsOverBudgetAndKeepsThoseAtBudget() {
        givenProvider(
                attraction("too-pricey", Set.of(Interest.ART), "60", "EUR", 30, ORIGIN_LAT, ORIGIN_LON),
                attraction("exactly-budget", Set.of(Interest.ART), "50", "EUR", 30, ORIGIN_LAT, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "EUR", 180));

        assertThat(ids(results)).containsExactly("exactly-budget");
    }

    @Test
    void excludesAttractionsWhoseVisitExceedsAvailableTime() {
        givenProvider(
                attraction("too-long", Set.of(Interest.ART), "5", "EUR", 200, ORIGIN_LAT, ORIGIN_LON),
                attraction("fits", Set.of(Interest.ART), "5", "EUR", 60, ORIGIN_LAT, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "EUR", 180));

        assertThat(ids(results)).containsExactly("fits");
    }

    @Test
    void excludesAttractionsWhoseWalkingPlusVisitExceedsAvailableTime() {
        // About 4.9 km away walks about 65 min; 65 + 120 > 150, but the visit alone (120) fits.
        givenProvider(
                attraction("far-walk", Set.of(Interest.ART), "5", "EUR", 120, NORTH_4_9_KM, ORIGIN_LON),
                attraction("near-walk", Set.of(Interest.ART), "5", "EUR", 120, ORIGIN_LAT, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "EUR", 150));

        assertThat(ids(results)).containsExactly("near-walk");
    }

    @Test
    void rejectsAttractionsBeyondFiveKilometres() {
        givenProvider(
                attraction("beyond-radius", Set.of(Interest.ART), "5", "EUR", 30, NORTH_6_1_KM, ORIGIN_LON),
                attraction("within-radius", Set.of(Interest.ART), "5", "EUR", 30, NORTH_4_9_KM, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "EUR", 720));

        assertThat(ids(results)).containsExactly("within-radius");
    }

    @Test
    void ranksNearerAttractionHigherWhenOtherFactorsAreEqual() {
        givenProvider(
                attraction("far", Set.of(Interest.ART), "5", "EUR", 30, NORTH_4_9_KM, ORIGIN_LON),
                attraction("near", Set.of(Interest.ART), "5", "EUR", 30, NORTH_0_5_KM, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "EUR", 720));

        assertThat(ids(results)).containsExactly("near", "far");
    }

    @Test
    void returnsAtMostThreeRecommendations() {
        givenProvider(
                attraction("a", Set.of(Interest.ART), "5", "EUR", 30, ORIGIN_LAT, ORIGIN_LON),
                attraction("b", Set.of(Interest.ART), "5", "EUR", 30, ORIGIN_LAT, ORIGIN_LON),
                attraction("c", Set.of(Interest.ART), "5", "EUR", 30, ORIGIN_LAT, ORIGIN_LON),
                attraction("d", Set.of(Interest.ART), "5", "EUR", 30, ORIGIN_LAT, ORIGIN_LON),
                attraction("e", Set.of(Interest.ART), "5", "EUR", 30, ORIGIN_LAT, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "EUR", 180));

        assertThat(results).hasSize(3);
    }

    @Test
    void returnsEmptyListWhenNoAttractionQualifies() {
        givenProvider(
                attraction("food-only", Set.of(Interest.FOOD), "5", "EUR", 30, ORIGIN_LAT, ORIGIN_LON));

        RecommendationResponse response = service.recommend(request(Set.of(Interest.ART), "50", "EUR", 180));

        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void returnsEmptyListWhenProviderReturnsNothing() {
        givenProvider();

        RecommendationResponse response = service.recommend(request(Set.of(Interest.ART), "50", "EUR", 180));

        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void doesNotCompareCostWhenCurrencyDiffers() {
        // 500 EUR would exceed a 50 USD budget if compared, but currencies differ, so it is not compared.
        givenProvider(
                attraction("euro-priced", Set.of(Interest.ART), "500", "EUR", 30, ORIGIN_LAT, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "USD", 180));

        assertThat(ids(results)).containsExactly("euro-priced");
        RecommendationDto dto = results.get(0);
        assertThat(dto.estimatedCost()).isEqualByComparingTo("500");
        assertThat(dto.currencyCode()).isEqualTo("EUR");
        assertThat(dto.description()).contains("differs from your USD budget");
    }

    @Test
    void breaksScoreTiesByAttractionIdRegardlessOfProviderOrder() {
        givenProvider(
                attraction("zeta", Set.of(Interest.ART), "5", "EUR", 30, ORIGIN_LAT, ORIGIN_LON),
                attraction("alpha", Set.of(Interest.ART), "5", "EUR", 30, ORIGIN_LAT, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "EUR", 180));

        assertThat(ids(results)).containsExactly("alpha", "zeta");
    }

    @Test
    void excludesAttractionsWithUnknownDuration() {
        givenProvider(
                attraction("unknown-duration", Set.of(Interest.ART), "5", "EUR", null, ORIGIN_LAT, ORIGIN_LON));

        assertThat(recommend(request(Set.of(Interest.ART), "50", "EUR", 180))).isEmpty();
    }

    @Test
    void keepsAttractionsWithUnknownCostWithoutInventingValues() {
        givenProvider(
                attraction("unknown-cost", Set.of(Interest.ART), null, null, 30, ORIGIN_LAT, ORIGIN_LON));

        List<RecommendationDto> results = recommend(request(Set.of(Interest.ART), "50", "EUR", 180));

        assertThat(ids(results)).containsExactly("unknown-cost");
        assertThat(results.get(0).estimatedCost()).isNull();
        assertThat(results.get(0).currencyCode()).isNull();
        assertThat(results.get(0).description()).contains("Estimated cost is unknown");
    }

    @Test
    void mapsDetailsAndBuildsEncodedDirectionsUrl() {
        givenProvider(
                attraction("colosseum", Set.of(Interest.HISTORY), "16", "EUR", 120, ORIGIN_LAT, ORIGIN_LON));

        RecommendationDto dto = recommend(request(Set.of(Interest.HISTORY), "50", "EUR", 180)).get(0);

        assertThat(dto.placeId()).isEqualTo("colosseum");
        assertThat(dto.name()).isEqualTo("Name colosseum");
        assertThat(dto.category()).isEqualTo("HISTORY");
        assertThat(dto.estimatedDurationMinutes()).isEqualTo(120);
        assertThat(dto.estimatedCost()).isEqualByComparingTo("16");
        assertThat(dto.currencyCode()).isEqualTo("EUR");
        assertThat(dto.description()).contains("Matches your interests: HISTORY");
        assertThat(dto.mapsUrl())
                .startsWith("https://www.google.com/maps/dir/?api=1")
                .contains("origin=41.8902,12.4922")
                .contains("destination=41.8902,12.4922")
                .contains("travelmode=walking");
    }

    private List<RecommendationDto> recommend(RecommendationRequest request) {
        return service.recommend(request).recommendations();
    }

    private void givenProvider(Attraction... attractions) {
        given(attractionProvider.findNearby(any(), any())).willReturn(List.of(attractions));
    }

    private static List<String> ids(List<RecommendationDto> results) {
        return results.stream().map(RecommendationDto::placeId).toList();
    }

    private static RecommendationRequest request(Set<Interest> interests, String budget, String currency, int minutes) {
        return new RecommendationRequest(
                new LocationRequest(new BigDecimal(ORIGIN_LAT), new BigDecimal(ORIGIN_LON)),
                interests, new BigDecimal(budget), currency, minutes);
    }

    private static Attraction attraction(String id, Set<Interest> categories, String cost, String currency,
                                         Integer durationMinutes, String latitude, String longitude) {
        return new Attraction(id, "Name " + id, categories,
                new BigDecimal(latitude), new BigDecimal(longitude),
                cost == null ? null : new BigDecimal(cost), currency, durationMinutes, "description");
    }
}
