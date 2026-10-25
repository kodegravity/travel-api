package com.aitravel.session.feedback;

import com.aitravel.session.logging.LogIds;
import com.aitravel.session.plan.TravelPlanService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each traveler's likes and dislikes. A disliked place is left out of every later search and Travel Mode refresh;
 * likes are kept so the traveler can find those places again.
 */
@Service
public class FeedbackService implements PlaceExclusions {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    /** The oldest ratings are dropped past this, keeping the item far below DynamoDB's 400 KB limit. */
    static final int MAX_ENTRIES = 300;
    private static final int MAX_SAVE_ATTEMPTS = 3;
    private static final TypeReference<List<PlaceFeedback>> ENTRIES = new TypeReference<>() {
    };

    private final FeedbackStore store;
    private final JsonMapper mapper;
    private final Clock clock;

    public FeedbackService(FeedbackStore store, JsonMapper mapper, Clock clock) {
        this.store = store;
        this.mapper = mapper;
        this.clock = clock;
    }

    public FeedbackResponse list(String subject) {
        return new FeedbackResponse(store.find(TravelPlanService.ownerKey(subject)).map(this::read).orElse(List.of()));
    }

    public FeedbackResponse rate(String subject, String placeId, RatePlaceRequest request) {
        Instant now = clock.instant();
        PlaceFeedback rated = new PlaceFeedback(placeId, request.rating(), blankToNull(request.name()),
                blankToNull(request.category()), blankToNull(request.cityId()), now);
        FeedbackResponse response = change(subject, entries -> {
            List<PlaceFeedback> next = new ArrayList<>(entries.size() + 1);
            next.add(rated);
            entries.stream().filter(entry -> !entry.placeId().equals(placeId)).limit(MAX_ENTRIES - 1).forEach(next::add);
            return next;
        });
        log.info("Place rated owner={} rating={} total={}", LogIds.subject(subject), request.rating(),
                response.places().size());
        return response;
    }

    public FeedbackResponse clear(String subject, String placeId) {
        FeedbackResponse response = change(subject, entries -> entries.stream()
                .filter(entry -> !entry.placeId().equals(placeId))
                .toList());
        log.info("Place rating cleared owner={} total={}", LogIds.subject(subject), response.places().size());
        return response;
    }

    @Override
    public Set<String> excludedPlaceIds(String subject) {
        try {
            return list(subject).places().stream()
                    .filter(entry -> entry.rating() == Rating.DISLIKE)
                    .map(PlaceFeedback::placeId)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException ex) {
            log.warn("Feedback unavailable, searching without exclusions owner={} cause={}", LogIds.subject(subject),
                    ex.getClass().getSimpleName());
            return Set.of();
        }
    }

    /** Read, change, and save, starting over when another device saved in between. */
    private FeedbackResponse change(String subject, UnaryOperator<List<PlaceFeedback>> edit) {
        String owner = TravelPlanService.ownerKey(subject);
        for (int attempt = 1; attempt <= MAX_SAVE_ATTEMPTS; attempt++) {
            Optional<StoredFeedback> current = store.find(owner);
            long version = current.map(StoredFeedback::version).orElse(0L);
            List<PlaceFeedback> next = edit.apply(current.map(this::read).orElse(List.of()));
            if (store.save(owner, version, new StoredFeedback(version + 1, mapper.writeValueAsString(next),
                    clock.instant()))) {
                return new FeedbackResponse(next);
            }
        }
        log.warn("Feedback save kept conflicting owner={}", LogIds.subject(subject));
        throw new FeedbackUnavailableException();
    }

    /** Newest first, one entry per place, even if an older document held duplicates. */
    private List<PlaceFeedback> read(StoredFeedback stored) {
        Map<String, PlaceFeedback> unique = new LinkedHashMap<>();
        mapper.readValue(stored.entriesJson(), ENTRIES).stream()
                .sorted(Comparator.comparing(PlaceFeedback::ratedAt).reversed())
                .forEach(entry -> unique.putIfAbsent(entry.placeId(), entry));
        return List.copyOf(unique.values());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
