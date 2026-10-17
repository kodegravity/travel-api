package com.aitravel.recommendation.service;

import com.aitravel.recommendation.model.Attraction;
import java.util.List;

/**
 * Chooses the best attractions from a list of already-eligible candidates and explains each choice.
 * Implementations must not discover places; they select only from {@code candidates}.
 */
public interface AIRecommendationService {

    List<AiRecommendation> recommend(TravelerPreferences preferences, List<Attraction> candidates);
}
