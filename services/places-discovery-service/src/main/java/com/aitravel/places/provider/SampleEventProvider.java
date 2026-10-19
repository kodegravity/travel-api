package com.aitravel.places.provider;

import com.aitravel.places.model.LiveEvent;
import com.aitravel.places.model.LiveEvent.EventType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Two sample events a short walk from the search point, for local development and mock mode. Labelled "Sample" and
 * carrying source "sample", so they can never pass for real listings. No external calls.
 */
@Component
@ConditionalOnProperty(prefix = "events", name = "provider", havingValue = "mock", matchIfMissing = true)
public class SampleEventProvider implements EventProvider {

    @Override
    public List<LiveEvent> findNearby(double latitude, double longitude, double radiusKm, Instant from, Instant to) {
        Instant soon = from.plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Instant later = from.plus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        return List.of(
                event("sample-game", "Sample: City Derby", EventType.SPORTS, "Soccer", "Sample Stadium",
                        latitude + 0.007, longitude + 0.004, soon, to, 0.9),
                event("sample-concert", "Sample: Evening Jazz", EventType.MUSIC, "Jazz", "Sample Hall",
                        latitude - 0.004, longitude - 0.006, later, to, 0.7))
                .stream()
                .filter(event -> event.startsAt() != null && event.distanceKm() <= radiusKm)
                .toList();
    }

    private static LiveEvent event(String id, String name, EventType type, String genre, String venue,
                                   double latitude, double longitude, Instant startsAt, Instant to, double km) {
        return new LiveEvent(id, name, type, genre, venue, latitude, longitude, startsAt.isAfter(to) ? null : startsAt,
                null, null, km, null, null, "sample");
    }
}
