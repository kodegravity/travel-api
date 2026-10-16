package com.aitravel.companion.service;

import com.aitravel.companion.model.Attraction;
import java.util.List;

/**
 * Chooses the best attractions from a list of already-eligible candidates and explains each choice.
 * Implementations must not discover places; they select only from {@code candidates}.
 */
public interface AIRecommendationService {

    List<AiRecommendation> recommend(TravelerPreferences preferences, List<Attraction> candidates);
}
