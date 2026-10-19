package com.aitravel.recommendation.live;

import com.aitravel.recommendation.dto.LocationRequest;
import com.aitravel.recommendation.live.LiveEvent.EventType;
import com.aitravel.recommendation.model.Interest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

final class LiveFixtures {

    static final Instant NOW = Instant.parse("2026-11-14T15:00:00Z");

    static final LiveEvent GAME = event("G1", "Real Madrid vs Sevilla", EventType.SPORTS, "Bernabéu", NOW.plusSeconds(5 * 3600), 4.2);
    static final LiveEvent JAZZ = event("C1", "Jazz Night", EventType.MUSIC, "Café Central", NOW.plusSeconds(3 * 3600), 0.4);
    static final LiveEvent PLAY = event("A1", "La Celestina", EventType.ARTS, "Teatro Español", NOW.plusSeconds(2 * 3600), 0.6);
    static final LiveEvent CIRCUS = event("F1", "Kids Circus", EventType.FAMILY, null, null, 1.5);

    private LiveFixtures() {
    }

    static LiveEvent event(String id, String name, EventType type, String venue, Instant startsAt, Double km) {
        return new LiveEvent(id, name, type, null, venue, 40.4, -3.7, startsAt, "2026-11-14", null, km,
                "https://www.ticketmaster.es/event/" + id, null, "ticketmaster");
    }

    static LiveSuggestionRequest request(Set<Interest> interests, Set<EventType> types, Set<String> excluded) {
        return new LiveSuggestionRequest(new LocationRequest(new BigDecimal("40.4169"), new BigDecimal("-3.7035")),
                interests, types, excluded);
    }
}
