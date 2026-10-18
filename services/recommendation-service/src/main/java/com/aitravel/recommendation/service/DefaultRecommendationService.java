package com.aitravel.recommendation.service;

import com.aitravel.recommendation.config.RecommendationProperties;
import com.aitravel.recommendation.dto.LocationRequest;
import com.aitravel.recommendation.dto.RecommendationDto;
import com.aitravel.recommendation.dto.RecommendationRequest;
import com.aitravel.recommendation.dto.RecommendationResponse;
import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import com.aitravel.recommendation.provider.AttractionProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class DefaultRecommendationService implements RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRecommendationService.class);
    private static final String MAPS_DIRECTIONS_URL = "https://www.google.com/maps/dir/";
    private static final Comparator<RankedRecommendation> RANKING_ORDER =
            Comparator.comparingDouble((RankedRecommendation r) -> r.score().totalScore())
                    .reversed()
                    .thenComparing(r -> r.candidate().attraction().id());

    private final AttractionProvider attractionProvider;
    private final RecommendationRankingService rankingService;
    private final RecommendationProperties properties;
    private final Optional<AIRecommendationService> aiRecommendationService;

    /** The AI engine is present only when {@code travel.ai.enabled=true}; otherwise ranking alone decides. */
    public DefaultRecommendationService(AttractionProvider attractionProvider,
                                        RecommendationRankingService rankingService,
                                        RecommendationProperties properties,
                                        Optional<AIRecommendationService> aiRecommendationService) {
        this.attractionProvider = attractionProvider;
        this.rankingService = rankingService;
        this.properties = properties;
        this.aiRecommendationService = aiRecommendationService;
    }

    @Override
    public RecommendationResponse recommend(RecommendationRequest request) {
        return recommend(request, Set.of());
    }

    @Override
    public RecommendationResponse recommend(RecommendationRequest request, Set<String> excludedPlaceIds) {
        LocationRequest origin = request.location();
        log.info("Requesting attractions from provider {}", attractionProvider.getClass().getSimpleName());
        List<Attraction> candidates = attractionProvider.findNearby(origin.latitude(), origin.longitude());
        if (candidates == null || candidates.isEmpty()) {
            log.info("Provider returned no attractions");
            return new RecommendationResponse(List.of());
        }
        log.debug("Provider returned {} candidate attractions", candidates.size());

        // Exclusions are applied before ranking so the top-N is filled from the remaining eligible candidates.
        List<RankedRecommendation> ranked = candidates.stream()
                .filter(attraction -> !excludedPlaceIds.contains(attraction.id()))
                .map(attraction -> evaluate(attraction, request))
                .flatMap(Optional::stream)
                .map(candidate -> new RankedRecommendation(candidate, rankingService.score(candidate, request)))
                .sorted(RANKING_ORDER)
                .toList();

        log.info("Eligible attractions after filtering and ranking: {} of {}", ranked.size(), candidates.size());
        if (ranked.isEmpty()) {
            return new RecommendationResponse(List.of());
        }
        if (aiRecommendationService.isPresent()) {
            log.info("Delegating {} eligible attractions to AI recommendation engine", ranked.size());
            Optional<RecommendationResponse> aiResponse = recommendWithAi(request, ranked, aiRecommendationService.get());
            if (aiResponse.isPresent()) {
                return aiResponse.get();
            }
        }
        return recommendByRanking(request, ranked);
    }

    private RecommendationResponse recommendByRanking(RecommendationRequest request, List<RankedRecommendation> ranked) {
        List<RecommendationDto> recommendations = ranked.stream()
                .limit(properties.maxRecommendations())
                .map(r -> toDto(r.candidate(), explanation(r, request), request))
                .toList();
        log.debug("Ranking selected {} recommendations", recommendations.size());
        return new RecommendationResponse(recommendations);
    }

    /**
     * The AI engine selects only from the eligible, ranked candidates (it never discovers places), so every
     * returned attraction has already passed the same budget, time, distance, and interest checks.
     *
     * <p>Returns empty when the AI fails, hits its daily limit, or selects nothing usable. The caller then falls
     * back to deterministic ranking, so eligible candidates are never hidden behind an AI outage.
     */
    private Optional<RecommendationResponse> recommendWithAi(RecommendationRequest request,
                                                             List<RankedRecommendation> ranked,
                                                             AIRecommendationService ai) {
        Map<String, RankedRecommendation> byId = new HashMap<>();
        ranked.forEach(r -> byId.putIfAbsent(r.candidate().attraction().id(), r));

        List<Attraction> attractions = ranked.stream().map(r -> r.candidate().attraction()).toList();
        TravelerPreferences preferences = new TravelerPreferences(
                Set.copyOf(request.interests()), request.maxBudget(), request.currencyCode(),
                request.availableMinutes());

        List<AiRecommendation> selections;
        try {
            selections = ai.recommend(preferences, attractions);
        } catch (AiRecommendationException ex) {
            log.warn("AI recommendation unavailable, falling back to ranking: {}", ex.getMessage());
            return Optional.empty();
        }
        List<RecommendationDto> recommendations = selections.stream()
                .filter(selection -> byId.containsKey(selection.attractionId()))
                .map(selection -> toDto(byId.get(selection.attractionId()).candidate(),
                        selection.reason(), request))
                .toList();
        if (recommendations.isEmpty()) {
            log.warn("AI selected no usable candidates out of {}, falling back to ranking", ranked.size());
            return Optional.empty();
        }
        return Optional.of(new RecommendationResponse(recommendations));
    }

    /** Applies all eligibility rules. Returns the candidate with travel metrics, or empty if excluded. */
    private Optional<CandidateAttraction> evaluate(Attraction attraction, RecommendationRequest request) {
        LocationRequest origin = request.location();
        double distanceKm = DistanceCalculator.haversineKm(
                origin.latitude().doubleValue(), origin.longitude().doubleValue(),
                attraction.latitude().doubleValue(), attraction.longitude().doubleValue());
        if (distanceKm > properties.searchRadiusKm()) {
            return Optional.empty();
        }

        if (Collections.disjoint(attraction.categories(), request.interests())) {
            return Optional.empty();
        }

        // Without a known duration the time fit cannot be verified, so the attraction is excluded.
        Integer visitMinutes = attraction.estimatedDurationMinutes();
        if (visitMinutes == null) {
            return Optional.empty();
        }

        double walkingMinutes = distanceKm / properties.walkingSpeedKmh() * 60.0;
        if (visitMinutes > request.availableMinutes()
                || walkingMinutes + visitMinutes > request.availableMinutes()) {
            return Optional.empty();
        }

        // Costs are compared only when the currency matches; no conversion is performed.
        if (attraction.hasCostIn(request.currencyCode())
                && attraction.estimatedCost().compareTo(request.maxBudget()) > 0) {
            return Optional.empty();
        }

        return Optional.of(new CandidateAttraction(attraction, distanceKm, walkingMinutes));
    }

    private RecommendationDto toDto(CandidateAttraction candidate, String description, RecommendationRequest request) {
        Attraction attraction = candidate.attraction();
        boolean hasCost = attraction.estimatedCost() != null;
        return new RecommendationDto(
                attraction.id(),
                attraction.name(),
                categoryLabel(attraction),
                description,
                attraction.latitude(),
                attraction.longitude(),
                attraction.estimatedDurationMinutes(),
                attraction.estimatedCost(),
                hasCost ? attraction.currencyCode() : null,
                mapsDirectionsUrl(request.location(), attraction));
    }

    /** Builds the reason from known data only. Costs and durations are described as estimates. */
    private String explanation(RankedRecommendation ranked, RecommendationRequest request) {
        CandidateAttraction candidate = ranked.candidate();
        Attraction attraction = candidate.attraction();
        List<String> sentences = new ArrayList<>();

        sentences.add("Matches your interests: " + joinInterests(matchedInterests(attraction, request)) + ".");
        sentences.add(String.format(Locale.ROOT, "About %.1f km away (about %d min walk).",
                candidate.distanceKm(), (int) Math.ceil(candidate.walkingMinutes())));
        sentences.add(costSentence(attraction, request));
        sentences.add(String.format(Locale.ROOT, "Estimated visit about %d min within your %d available minutes.",
                attraction.estimatedDurationMinutes(), request.availableMinutes()));
        return String.join(" ", sentences);
    }

    private String costSentence(Attraction attraction, RecommendationRequest request) {
        if (attraction.estimatedCost() == null) {
            return "Estimated cost is unknown, so it was not compared with your budget.";
        }
        if (!attraction.hasCostIn(request.currencyCode())) {
            return String.format(Locale.ROOT,
                    "Estimated cost is in %s, which differs from your %s budget, so it was not compared.",
                    attraction.currencyCode(), request.currencyCode());
        }
        return String.format(Locale.ROOT, "Estimated cost %s %s is within your budget of %s %s.",
                attraction.currencyCode(), formatMoney(attraction.estimatedCost()),
                request.currencyCode(), formatMoney(request.maxBudget()));
    }

    private static Set<Interest> matchedInterests(Attraction attraction, RecommendationRequest request) {
        EnumSet<Interest> matched = EnumSet.noneOf(Interest.class);
        matched.addAll(attraction.categories());
        matched.retainAll(request.interests());
        return matched;
    }

    private static String categoryLabel(Attraction attraction) {
        return attraction.categories().stream()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private static String joinInterests(Set<Interest> interests) {
        return interests.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    private static String formatMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String mapsDirectionsUrl(LocationRequest origin, Attraction attraction) {
        return UriComponentsBuilder.fromUriString(MAPS_DIRECTIONS_URL)
                .queryParam("api", 1)
                .queryParam("origin", coordinates(origin.latitude(), origin.longitude()))
                .queryParam("destination", coordinates(attraction.latitude(), attraction.longitude()))
                .queryParam("travelmode", "walking")
                .build()
                .encode()
                .toUriString();
    }

    private static String coordinates(BigDecimal latitude, BigDecimal longitude) {
        return latitude.toPlainString() + "," + longitude.toPlainString();
    }

    private record RankedRecommendation(CandidateAttraction candidate,
                                        RecommendationRankingService.ScoreBreakdown score) {
    }
}
