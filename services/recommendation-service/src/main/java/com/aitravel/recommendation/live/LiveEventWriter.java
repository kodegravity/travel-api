package com.aitravel.recommendation.live;

import java.util.List;

/** Chooses which events to suggest and writes the alert text. Implementations select only from {@code candidates}. */
public interface LiveEventWriter {

    List<LiveSuggestion> write(LiveSuggestionRequest request, List<LiveEvent> candidates, int maxSuggestions);
}
