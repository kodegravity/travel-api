package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.places.model.PlaceDetails;
import org.junit.jupiter.api.Test;

class MockPlaceDetailsProviderTest {

    private final MockPlaceDetailsProvider provider = new MockPlaceDetailsProvider();

    @Test
    void returnsSampleDetailsForAMockAttraction() {
        PlaceDetails details = provider.findDetails("pantheon").orElseThrow();

        assertThat(details.name()).isEqualTo("Pantheon");
        assertThat(details.summary()).isNotBlank();
        assertThat(details.rating()).isNull();
        assertThat(details.source()).isEqualTo("sample");
    }

    @Test
    void unknownIdIsEmpty() {
        assertThat(provider.findDetails("nowhere")).isEmpty();
    }
}
