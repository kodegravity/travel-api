package com.aitravel.places.provider;

import com.aitravel.places.model.LiveEvent;
import java.time.Instant;
import java.util.List;

/** Ticketed events near a point, starting within a time window, soonest first. */
public interface EventProvider {

    List<LiveEvent> findNearby(double latitude, double longitude, double radiusKm, Instant from, Instant to);
}
