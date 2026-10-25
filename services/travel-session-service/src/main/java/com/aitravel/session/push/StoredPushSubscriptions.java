package com.aitravel.session.push;

import java.time.Instant;

/** A traveler's subscriptions as stored: the list as JSON, with the version a save must match. */
public record StoredPushSubscriptions(long version, String subscriptionsJson, Instant savedAt) {
}
