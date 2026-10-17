package com.aitravel.recommendation.provider;

import com.aitravel.recommendation.model.Attraction;
import java.math.BigDecimal;
import java.util.List;

/**
 * Source of candidate attractions. Implementations may return candidates beyond the search radius;
 * the recommendation service applies distance filtering itself.
 */
public interface AttractionProvider {

    List<Attraction> findNearby(BigDecimal latitude, BigDecimal longitude);
}
