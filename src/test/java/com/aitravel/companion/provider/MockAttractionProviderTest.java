package com.aitravel.companion.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.companion.model.Attraction;
import com.aitravel.companion.model.Interest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MockAttractionProviderTest {

    private final MockAttractionProvider provider = new MockAttractionProvider();

    @Test
    void returnsBetweenEightAndTenUniqueRomanAttractions() {
        List<Attraction> attractions = provider.findNearby(new BigDecimal("41.9"), new BigDecimal("12.5"));

        assertThat(attractions).hasSizeBetween(8, 10);
        assertThat(attractions).extracting(Attraction::id).doesNotHaveDuplicates();
        assertThat(attractions).allSatisfy(attraction -> {
            assertThat(attraction.latitude()).isBetween(new BigDecimal("41.75"), new BigDecimal("42.0"));
            assertThat(attraction.longitude()).isBetween(new BigDecimal("12.3"), new BigDecimal("12.6"));
            assertThat(attraction.categories()).isNotEmpty();
            assertThat(attraction.estimatedCost()).isNotNull();
            assertThat(attraction.currencyCode()).isEqualTo("EUR");
            assertThat(attraction.estimatedDurationMinutes()).isPositive();
        });
    }

    @Test
    void coversEveryInterestCategory() {
        List<Attraction> attractions = provider.findNearby(new BigDecimal("41.9"), new BigDecimal("12.5"));

        Set<Interest> categories = attractions.stream()
                .flatMap(attraction -> attraction.categories().stream())
                .collect(Collectors.toSet());
        assertThat(categories).containsExactlyInAnyOrder(Interest.values());
    }
}
