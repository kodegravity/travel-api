package com.aitravel.session.plan;

import com.aitravel.session.logging.LogIds;
import com.aitravel.session.messaging.EventOutbox;
import com.aitravel.session.messaging.EventType;
import com.aitravel.session.messaging.events.ItineraryUpdated;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps each traveler's trips and itineraries so they follow them across devices. The web app merges; this service
 * only stores the merged plan, refusing a save made from a stale version so no device overwrites newer work.
 */
@Service
public class TravelPlanService {

    private static final Logger log = LoggerFactory.getLogger(TravelPlanService.class);

    /** Well under DynamoDB's 400 KB item limit, leaving room for the other attributes. */
    static final int MAX_PLAN_BYTES = 350_000;
    private static final String CITY_PREFIX = "city:";
    private static final TypeReference<Map<String, TravelPlanEntry>> ENTRIES = new TypeReference<>() {
    };

    private final TravelPlanStore store;
    private final JsonMapper mapper;
    private final EventOutbox outbox;
    private final Clock clock;

    public TravelPlanService(TravelPlanStore store, JsonMapper mapper, EventOutbox outbox, Clock clock) {
        this.store = store;
        this.mapper = mapper;
        this.outbox = outbox;
        this.clock = clock;
    }

    public TravelPlanResponse get(String subject) {
        String owner = ownerKey(subject);
        return store.find(owner)
                .map(plan -> new TravelPlanResponse(owner, plan.version(), read(plan)))
                .orElseGet(() -> new TravelPlanResponse(owner, 0, Map.of()));
    }

    public TravelPlanResponse save(String subject, SaveTravelPlanRequest request) {
        String owner = ownerKey(subject);
        String json = mapper.writeValueAsString(request.entries());
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PLAN_BYTES) {
            log.info("Travel plan rejected as too large owner={} entries={}", LogIds.subject(subject),
                    request.entries().size());
            throw new TravelPlanException(TravelPlanException.Reason.TOO_LARGE);
        }
        // Only needed to tell which itineraries changed, so skipped when no events are published.
        Map<String, TravelPlanEntry> previous = outbox.enabled()
                ? store.find(owner).map(this::read).orElse(Map.of())
                : Map.of();
        long version = request.baseVersion() + 1;
        if (!store.save(owner, request.baseVersion(), new StoredTravelPlan(version, json, clock.instant()))) {
            log.info("Travel plan save conflicted owner={} baseVersion={}", LogIds.subject(subject),
                    request.baseVersion());
            throw new TravelPlanException(TravelPlanException.Reason.VERSION_CONFLICT);
        }
        log.info("Travel plan saved owner={} version={} entries={}", LogIds.subject(subject), version,
                request.entries().size());
        publishItineraryChanges(subject, previous, request.entries());
        return new TravelPlanResponse(owner, version, request.entries());
    }

    private void publishItineraryChanges(String subject, Map<String, TravelPlanEntry> previous,
                                         Map<String, TravelPlanEntry> current) {
        if (!outbox.enabled()) {
            return;
        }
        current.forEach((key, entry) -> {
            if (!key.startsWith(CITY_PREFIX)) {
                return;
            }
            JsonNode itinerary = itinerary(entry);
            if (itinerary == null || Objects.equals(itinerary, itinerary(previous.get(key)))) {
                return;
            }
            String cityId = key.substring(CITY_PREFIX.length());
            JsonNode days = itinerary.get("days");
            int dayCount = 0;
            int stopCount = 0;
            if (days != null && days.isArray()) {
                dayCount = days.size();
                for (int i = 0; i < days.size(); i++) {
                    JsonNode stops = days.get(i).get("stops");
                    stopCount += stops != null && stops.isArray() ? stops.size() : 0;
                }
            }
            outbox.publishStandalone(EventType.ITINERARY_UPDATED,
                    new ItineraryUpdated(LogIds.subject(subject) + ":" + cityId, cityId, dayCount, stopCount));
        });
    }

    private static JsonNode itinerary(TravelPlanEntry entry) {
        if (entry == null || entry.deleted()) {
            return null;
        }
        JsonNode itinerary = entry.data().get("itinerary");
        return itinerary != null && itinerary.isObject() ? itinerary : null;
    }

    private Map<String, TravelPlanEntry> read(StoredTravelPlan plan) {
        return mapper.readValue(plan.entriesJson(), ENTRIES);
    }

    /** Full SHA-256 of the Okta subject: the storage key, and the ownerRef the web app compares. */
    static String ownerKey(String subject) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
