package com.aitravel.session.messaging.events;

import com.aitravel.session.model.Interest;
import java.util.Set;

/** A traveler started Active Travel Mode. {@code ownerRef} is a one-way hash, never the identity itself. */
public record TravelSessionStarted(String sessionId, String ownerRef, Set<Interest> interests, String currencyCode,
                                   Integer availableMinutes) {
}
