package com.aitravel.places.provider;

import com.aitravel.places.model.LiveEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Live events turned off ({@code events.provider=none}): every search is empty. */
@Component
@ConditionalOnProperty(prefix = "events", name = "provider", havingValue = "none")
public class NoEventProvider implements EventProvider {

    @Override
    public List<LiveEvent> findNearby(double latitude, double longitude, double radiusKm, Instant from, Instant to) {
        return List.of();
    }
}
