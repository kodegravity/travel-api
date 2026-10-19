package com.aitravel.recommendation.live;

import com.aitravel.recommendation.exception.AiRecommendationException;
import com.aitravel.recommendation.live.LiveEvent.EventType;
import com.aitravel.recommendation.model.Interest;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Events worth an alert right now: nearby, soon, of a kind the traveler wants, and not suggested before. The AI
 * chooses and writes when it is on and within its cap; otherwise the soonest, nearest matches get a plain alert.
 */
@Service
@EnableConfigurationProperties(LiveSuggestionProperties.class)
public class LiveSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(LiveSuggestionService.class);

    /** Event kinds that suit each interest, so they rank first. */
    private static final Map<Interest, Set<EventType>> AFFINITY = Map.of(
            Interest.ART, Set.of(EventType.ARTS, EventType.MUSIC),
            Interest.LOCAL_EXPERIENCES, Set.of(EventType.SPORTS, EventType.MUSIC, EventType.FAMILY, EventType.ARTS));

    private final EventsClient eventsClient;
    private final LiveSuggestionProperties properties;
    private final ObjectProvider<LiveEventWriter> aiWriter;

    public LiveSuggestionService(EventsClient eventsClient, LiveSuggestionProperties properties,
                                 ObjectProvider<LiveEventWriter> aiWriter) {
        this.eventsClient = eventsClient;
        this.properties = properties;
        this.aiWriter = aiWriter;
    }

    public List<LiveSuggestion> suggest(LiveSuggestionRequest request) {
        List<LiveEvent> events = eventsClient.nearby(request.location().latitude().doubleValue(),
                request.location().longitude().doubleValue(), properties.radiusKm(), properties.hours());
        Set<EventType> wanted = request.eventTypes();
        Set<String> excluded = request.excludedOrEmpty();
        Set<EventType> favoured = request.interestsOrEmpty().stream()
                .flatMap(interest -> AFFINITY.getOrDefault(interest, Set.of()).stream())
                .collect(java.util.stream.Collectors.toSet());
        List<LiveEvent> candidates = events.stream()
                .filter(event -> event.id() != null && event.name() != null)
                .filter(event -> wanted.contains(event.type() == null ? EventType.OTHER : event.type()))
                .filter(event -> !excluded.contains(event.id()))
                .sorted(Comparator.comparing((LiveEvent event) -> favoured.contains(event.type()) ? 0 : 1)
                        .thenComparing(LiveEvent::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LiveEvent::distanceKm, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(properties.maxCandidates())
                .toList();
        if (candidates.isEmpty()) {
            log.info("Live suggestions events={} candidates=0", events.size());
            return List.of();
        }

        LiveEventWriter writer = aiWriter.getIfAvailable();
        if (writer != null) {
            try {
                List<LiveSuggestion> written = writer.write(request, candidates, properties.maxSuggestions());
                log.info("Live suggestions events={} candidates={} suggested={} source=ai", events.size(),
                        candidates.size(), written.size());
                return written;
            } catch (AiRecommendationException ex) {
                log.info("AI live events unavailable ({}), using rule-based picks", ex.getMessage());
            }
        }
        List<LiveSuggestion> picked = candidates.stream().limit(properties.maxSuggestions()).map(this::template).toList();
        log.info("Live suggestions events={} candidates={} suggested={} source=rules", events.size(), candidates.size(),
                picked.size());
        return picked;
    }

    private LiveSuggestion template(LiveEvent event) {
        String headline = switch (event.type() == null ? EventType.OTHER : event.type()) {
            case SPORTS -> "Game nearby: ";
            case MUSIC -> "Live music nearby: ";
            case ARTS -> "Show nearby: ";
            case FAMILY -> "Family event nearby: ";
            case OTHER -> "Happening nearby: ";
        } + event.name();
        String where = event.venueName() == null ? "" : event.venueName() + ", ";
        String message = event.distanceKm() == null
                ? (event.venueName() == null ? "Close to where you are." : event.venueName() + ".")
                : where + String.format(Locale.ROOT, "about %.1f km from you.", event.distanceKm());
        return new LiveSuggestion(event, headline, message, false);
    }
}
