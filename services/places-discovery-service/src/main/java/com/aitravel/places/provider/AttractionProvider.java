package com.aitravel.places.provider;

import com.aitravel.places.model.Attraction;
import java.math.BigDecimal;
import java.util.List;

/**
 * Source of candidate attractions. Implementations may return candidates beyond the search radius;
 * the caller applies distance filtering itself.
 */
public interface AttractionProvider {

    /**
     * @param latitude      search centre latitude
     * @param longitude     search centre longitude
     * @param searchRadiusKm maximum straight-line distance from the centre, in kilometres
     */
    List<Attraction> findNearby(BigDecimal latitude, BigDecimal longitude, double searchRadiusKm);
}
