package com.aitravel.places.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.places.provider.AttractionProvider;
import com.aitravel.places.provider.GooglePlaceDetailsProvider;
import com.aitravel.places.provider.GooglePlacesAttractionProvider;
import com.aitravel.places.provider.GoogleRoutesProvider;
import com.aitravel.places.provider.PlaceDetailsProvider;
import com.aitravel.places.provider.RouteProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Starts the whole application in Google mode, as it runs in production, so wiring errors in the Google beans fail
 * here instead of at deploy time. Building the beans makes no Google calls.
 */
@SpringBootTest
@TestPropertySource(properties = {
        JwtTestSupport.ISSUER_PROPERTY,
        JwtTestSupport.AUDIENCE_PROPERTY,
        "attraction.provider=google",
        "google.places.api-key=test-key-not-real"
})
class GoogleProviderWiringTest {

    @Autowired
    private AttractionProvider attractionProvider;

    @Autowired
    private PlaceDetailsProvider placeDetailsProvider;

    @Autowired
    private RouteProvider routeProvider;

    @Test
    void googleBeansAreCreated() {
        assertThat(attractionProvider).isInstanceOf(GooglePlacesAttractionProvider.class);
        assertThat(placeDetailsProvider).isInstanceOf(GooglePlaceDetailsProvider.class);
        assertThat(routeProvider).isInstanceOf(GoogleRoutesProvider.class);
    }
}
