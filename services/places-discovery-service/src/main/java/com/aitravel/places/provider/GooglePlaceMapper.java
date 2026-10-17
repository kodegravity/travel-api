package com.aitravel.places.provider;

import com.aitravel.places.model.Attraction;
import com.aitravel.places.model.Interest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts a Google place into the application's {@link Attraction}.
 *
 * <p>Google does not return a visit duration or price for these fields, so the visit duration is a
 * development estimate taken from the place's interests. The longest estimate is used, which avoids
 * overstating how well a visit fits the available time. Cost is left unknown, and the Google Maps URI
 * is not stored: navigation URLs are built from coordinates, as for all providers.
 */
final class GooglePlaceMapper {

    private static final Map<Interest, Integer> ESTIMATED_VISIT_MINUTES = Map.of(
            Interest.HISTORY, 90,
            Interest.ART, 90,
            Interest.NATURE, 60,
            Interest.FOOD, 60,
            Interest.SHOPPING, 60,
            Interest.LOCAL_EXPERIENCES, 60
    );

    private GooglePlaceMapper() {
    }

    /** Returns empty when the place lacks an id, name or location, or has no mapped interest. */
    static Optional<Attraction> toAttraction(GooglePlacesApi.Place place) {
        if (place == null || isBlank(place.id()) || place.location() == null
                || place.displayName() == null || isBlank(place.displayName().text())) {
            return Optional.empty();
        }
        Set<Interest> interests = GooglePlaceTypeMapping.interestsFor(place.types());
        if (interests.isEmpty()) {
            return Optional.empty();
        }
        int visitMinutes = interests.stream()
                .mapToInt(ESTIMATED_VISIT_MINUTES::get)
                .max()
                .orElseThrow();
        return Optional.of(new Attraction(
                place.id(),
                place.displayName().text(),
                interests,
                BigDecimal.valueOf(place.location().latitude()),
                BigDecimal.valueOf(place.location().longitude()),
                null,
                null,
                visitMinutes,
                null));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
