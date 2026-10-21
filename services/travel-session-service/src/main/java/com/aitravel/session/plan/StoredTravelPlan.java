package com.aitravel.session.plan;

import java.time.Instant;

/** A plan as stored: the entries as JSON, with the version a save must match. */
public record StoredTravelPlan(long version, String entriesJson, Instant savedAt) {
}
