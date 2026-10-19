package com.aitravel.recommendation.chat;

import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.exception.AttractionProviderException;
import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import com.aitravel.recommendation.provider.AttractionProvider;
import com.aitravel.recommendation.service.DistanceCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Answers one chat turn: gathers the weather and real nearby places (each cached per area, so a conversation does not
 * repeat paid searches), lets the AI reply, and falls back to a plain rule-based answer when the AI is off, over its
 * cap, slow, or failing.
 */
@Service
@EnableConfigurationProperties(ChatProperties.class)
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int FALLBACK_PICKS = 3;

    private final AttractionProvider attractionProvider;
    private final WeatherClient weatherClient;
    private final ChatProperties properties;
    private final ObjectProvider<ChatAssistant> assistant;
    private final AreaCache<List<Attraction>> places;

    @Autowired
    public ChatService(AttractionProvider attractionProvider, WeatherClient weatherClient, ChatProperties properties,
                       ObjectProvider<ChatAssistant> assistant) {
        this(attractionProvider, weatherClient, properties, assistant, Clock.systemUTC());
    }

    ChatService(AttractionProvider attractionProvider, WeatherClient weatherClient, ChatProperties properties,
                ObjectProvider<ChatAssistant> assistant, Clock clock) {
        this.attractionProvider = attractionProvider;
        this.weatherClient = weatherClient;
        this.properties = properties;
        this.assistant = assistant;
        this.places = new AreaCache<>(properties.cacheTtl(), clock);
    }

    public ChatResponse reply(ChatRequest request) {
        var location = request.context().location();
        Optional<ChatResponse.Weather> weather = location == null ? Optional.empty()
                : weatherClient.current(location.latitude(), location.longitude());
        List<ChatCandidate> candidates = location == null ? List.of()
                : candidates(location.latitude(), location.longitude(), request.context().interestsOrEmpty(),
                        weather.map(ChatResponse.Weather::rainLikely).orElse(false));
        Map<String, ChatCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(c -> c.attraction().id(), Function.identity(), (a, b) -> a));

        ChatAssistant ai = assistant.getIfAvailable();
        if (ai != null) {
            try {
                ChatAssistant.Draft draft = ai.reply(request, candidates, weather);
                List<ChatResponse.Suggestion> suggestions = draft.picks().stream()
                        .map(pick -> suggestion(byId.get(pick.placeId()), pick.reason()))
                        .toList();
                log.info("Chat reply source=ai candidates={} suggestions={} weather={}", candidates.size(),
                        suggestions.size(), weather.isPresent());
                return new ChatResponse(draft.reply(), suggestions, weather.orElse(null), true);
            } catch (AiRecommendationException ex) {
                log.info("AI chat unavailable ({}), answering without AI", ex.getMessage());
            }
        }
        return fallback(candidates, weather);
    }

    /** Real nearby places, best first: matching an interest, suiting the weather, then closest. */
    private List<ChatCandidate> candidates(BigDecimal latitude, BigDecimal longitude, Set<Interest> interests, boolean rainLikely) {
        List<Attraction> nearby;
        try {
            nearby = places.get(AreaCache.key(latitude, longitude), () -> attractionProvider.findNearby(latitude, longitude));
        } catch (AttractionProviderException ex) {
            log.info("Nearby places unavailable for chat ({}), answering without them", ex.getMessage());
            return List.of();
        }
        return nearby.stream()
                .map(a -> new ChatCandidate(a, DistanceCalculator.haversineKm(latitude.doubleValue(), longitude.doubleValue(),
                        a.latitude().doubleValue(), a.longitude().doubleValue()), ChatCandidate.settingOf(a.categories())))
                .filter(c -> c.distanceKm() <= properties.radiusKm())
                .sorted(Comparator
                        .comparing((ChatCandidate c) -> interests.isEmpty() || c.attraction().categories().stream()
                                .anyMatch(interests::contains) ? 0 : 1)
                        .thenComparing(c -> rainLikely && c.setting() == ChatCandidate.Setting.OUTDOOR ? 1 : 0)
                        .thenComparingDouble(ChatCandidate::distanceKm))
                .limit(properties.maxCandidates())
                .toList();
    }

    private ChatResponse fallback(List<ChatCandidate> candidates, Optional<ChatResponse.Weather> weather) {
        List<ChatResponse.Suggestion> suggestions = candidates.stream().limit(FALLBACK_PICKS)
                .map(c -> suggestion(c, reasonFor(c, weather)))
                .toList();
        String reply;
        if (suggestions.isEmpty()) {
            reply = "The AI assistant isn't available right now, and I don't have nearby places to suggest. "
                    + "Share your location or open Explore to see what's around you.";
        } else {
            String weatherNote = weather.filter(ChatResponse.Weather::rainLikely)
                    .map(w -> " Rain is likely soon, so these lean indoors.").orElse("");
            reply = "The AI assistant isn't available right now, but here are good options close to you, "
                    + "based on your interests." + weatherNote + " Check opening hours before you go.";
        }
        log.info("Chat reply source=rules candidates={} suggestions={}", candidates.size(), suggestions.size());
        return new ChatResponse(reply, suggestions, weather.orElse(null), false);
    }

    private static String reasonFor(ChatCandidate candidate, Optional<ChatResponse.Weather> weather) {
        String where = String.format(java.util.Locale.ROOT, "About %.1f km away", candidate.distanceKm());
        if (weather.map(ChatResponse.Weather::rainLikely).orElse(false) && candidate.setting() == ChatCandidate.Setting.INDOOR) {
            return where + ", and indoors if the rain comes.";
        }
        return where + ".";
    }

    private static ChatResponse.Suggestion suggestion(ChatCandidate candidate, String reason) {
        Attraction a = candidate.attraction();
        return new ChatResponse.Suggestion(a.id(), a.name(), a.categories(), a.latitude(), a.longitude(),
                Math.round(candidate.distanceKm() * 10) / 10.0, a.estimatedDurationMinutes(),
                a.estimatedCost() == null ? null : a.estimatedCost().setScale(2, RoundingMode.HALF_UP), a.currencyCode(),
                reason);
    }
}
