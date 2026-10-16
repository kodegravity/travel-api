package com.aitravel.companion.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.companion.model.Interest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GooglePlaceMapperTest {

    @Test
    void usesLongestEstimatedVisitWhenPlaceHasSeveralInterests() {
        // museum -> ART (90), tourist_attraction -> LOCAL_EXPERIENCES (60): the longer estimate wins.
        var place = place("p1", "Museum Hub", List.of("museum", "tourist_attraction"));

        var attraction = GooglePlaceMapper.toAttraction(place).orElseThrow();

        assertThat(attraction.categories()).containsExactlyInAnyOrder(Interest.ART, Interest.LOCAL_EXPERIENCES);
        assertThat(attraction.estimatedDurationMinutes()).isEqualTo(90);
    }

    @Test
    void ignoresUnmappedTypesAndDropsPlacesWithNoMappedInterest() {
        assertThat(GooglePlaceMapper.toAttraction(place("p2", "Bank", List.of("bank", "finance"))))
                .isEmpty();
    }

    @Test
    void dropsPlacesMissingIdNameOrLocation() {
        assertThat(GooglePlaceMapper.toAttraction(place("", "Named", List.of("museum")))).isEmpty();
        assertThat(GooglePlaceMapper.toAttraction(place("p3", null, List.of("museum")))).isEmpty();
        assertThat(GooglePlaceMapper.toAttraction(
                new GooglePlacesApi.Place("p4", new GooglePlacesApi.DisplayName("No location", null),
                        null, List.of("museum"), null))).isEmpty();
        assertThat(GooglePlaceMapper.toAttraction(null)).isEqualTo(Optional.empty());
    }

    @Test
    void leavesCostAndCurrencyUnknown() {
        var attraction = GooglePlaceMapper.toAttraction(place("p5", "Park", List.of("park"))).orElseThrow();

        assertThat(attraction.estimatedCost()).isNull();
        assertThat(attraction.currencyCode()).isNull();
        assertThat(attraction.description()).isNull();
    }

    private static GooglePlacesApi.Place place(String id, String name, List<String> types) {
        return new GooglePlacesApi.Place(
                id,
                name == null ? null : new GooglePlacesApi.DisplayName(name, "en"),
                new GooglePlacesApi.LatLng(41.89, 12.49),
                types,
                null);
    }
}
