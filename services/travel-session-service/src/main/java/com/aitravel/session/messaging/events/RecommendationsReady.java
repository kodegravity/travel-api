package com.aitravel.session.messaging.events;

import java.util.List;

/** New places were recommended for a session; ids only, so consumers look up what they need. */
public record RecommendationsReady(String sessionId, List<String> placeIds, String source) {
}
