package com.aitravel.companion.provider;

import com.aitravel.companion.model.Interest;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps Google place types to application {@link Interest}s. This is an initial mapping, not the
 * complete taxonomy. Unmapped Google types are ignored, and a place with no mapped type is dropped.
 *
 * <p>Every key was checked against Table A of the official Places (New) place-type list, because
 * Table A is the only set accepted in {@code includedTypes}.
 */
final class GooglePlaceTypeMapping {

    private static final Map<String, Set<Interest>> TYPE_TO_INTERESTS = Map.ofEntries(
            Map.entry("historical_landmark", Set.of(Interest.HISTORY)),
            Map.entry("historical_place", Set.of(Interest.HISTORY)),
            Map.entry("monument", Set.of(Interest.HISTORY)),
            Map.entry("church", Set.of(Interest.HISTORY)),
            Map.entry("cultural_landmark", Set.of(Interest.HISTORY)),
            Map.entry("art_gallery", Set.of(Interest.ART)),
            Map.entry("museum", Set.of(Interest.ART)),
            Map.entry("park", Set.of(Interest.NATURE)),
            Map.entry("national_park", Set.of(Interest.NATURE)),
            Map.entry("botanical_garden", Set.of(Interest.NATURE)),
            Map.entry("garden", Set.of(Interest.NATURE)),
            Map.entry("hiking_area", Set.of(Interest.NATURE)),
            Map.entry("beach", Set.of(Interest.NATURE)),
            Map.entry("restaurant", Set.of(Interest.FOOD)),
            Map.entry("cafe", Set.of(Interest.FOOD)),
            Map.entry("bakery", Set.of(Interest.FOOD)),
            Map.entry("meal_takeaway", Set.of(Interest.FOOD)),
            Map.entry("food_court", Set.of(Interest.FOOD)),
            Map.entry("shopping_mall", Set.of(Interest.SHOPPING)),
            Map.entry("clothing_store", Set.of(Interest.SHOPPING)),
            Map.entry("book_store", Set.of(Interest.SHOPPING)),
            Map.entry("gift_shop", Set.of(Interest.SHOPPING)),
            Map.entry("tourist_attraction", Set.of(Interest.LOCAL_EXPERIENCES)),
            Map.entry("market", Set.of(Interest.LOCAL_EXPERIENCES))
    );

    private GooglePlaceTypeMapping() {
    }

    /** Sorted, so the request body is stable between calls. */
    static List<String> includedTypes() {
        return List.copyOf(new TreeSet<>(TYPE_TO_INTERESTS.keySet()));
    }

    static Set<Interest> interestsFor(Collection<String> placeTypes) {
        EnumSet<Interest> interests = EnumSet.noneOf(Interest.class);
        if (placeTypes == null) {
            return interests;
        }
        for (String placeType : placeTypes) {
            interests.addAll(TYPE_TO_INTERESTS.getOrDefault(placeType, Set.of()));
        }
        return interests;
    }
}
