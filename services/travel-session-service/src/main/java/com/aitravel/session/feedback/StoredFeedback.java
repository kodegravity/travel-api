package com.aitravel.session.feedback;

import java.time.Instant;

/** A traveler's feedback as stored: the entries as JSON, with the version a save must match. */
public record StoredFeedback(long version, String entriesJson, Instant savedAt) {
}
