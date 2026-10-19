package com.aitravel.recommendation.chat;

import java.util.List;
import java.util.Optional;

/** Writes the assistant's reply. Implementations suggest only places from {@code candidates}. */
public interface ChatAssistant {

    Draft reply(ChatRequest request, List<ChatCandidate> candidates, Optional<ChatResponse.Weather> weather);

    /** The reply text and the chosen candidates, each with a reason. */
    record Draft(String reply, List<Pick> picks) {
    }

    record Pick(String placeId, String reason) {
    }
}
