package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.places.model.Interest;
import java.util.List;
import org.junit.jupiter.api.Test;

class GooglePlaceTypeMappingTest {

    @Test
    void includedTypesCoverTheInitialCategoryMappingInStableOrder() {
        List<String> types = GooglePlaceTypeMapping.includedTypes();

        assertThat(types).contains("tourist_attraction", "museum", "art_gallery", "historical_landmark",
                "park", "restaurant", "cafe");
        assertThat(types).isSorted();
        assertThat(types).doesNotHaveDuplicates();
    }

    @Test
    void mapsEachInterestToItsPlaceTypes() {
        assertThat(GooglePlaceTypeMapping.interestsFor(List.of("historical_landmark"))).containsExactly(Interest.HISTORY);
        assertThat(GooglePlaceTypeMapping.interestsFor(List.of("art_gallery"))).containsExactly(Interest.ART);
        assertThat(GooglePlaceTypeMapping.interestsFor(List.of("garden"))).containsExactly(Interest.NATURE);
        assertThat(GooglePlaceTypeMapping.interestsFor(List.of("cafe"))).containsExactly(Interest.FOOD);
        assertThat(GooglePlaceTypeMapping.interestsFor(List.of("book_store"))).containsExactly(Interest.SHOPPING);
        assertThat(GooglePlaceTypeMapping.interestsFor(List.of("market"))).containsExactly(Interest.LOCAL_EXPERIENCES);
    }

    @Test
    void returnsNoInterestsForUnknownOrMissingTypes() {
        assertThat(GooglePlaceTypeMapping.interestsFor(List.of("atm", "bank"))).isEmpty();
        assertThat(GooglePlaceTypeMapping.interestsFor(null)).isEmpty();
    }
}
