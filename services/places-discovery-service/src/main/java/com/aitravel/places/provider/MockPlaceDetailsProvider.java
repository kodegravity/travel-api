package com.aitravel.places.provider;

import com.aitravel.places.model.Attraction;
import com.aitravel.places.model.PlaceDetails;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Details for the mock Rome attractions: name, location, and description only. No Google calls. */
@Component
@ConditionalOnProperty(prefix = "attraction", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockPlaceDetailsProvider implements PlaceDetailsProvider {

    @Override
    public Optional<PlaceDetails> findDetails(String placeId) {
        return MockAttractionProvider.sampleAttractions().stream()
                .filter(attraction -> attraction.id().equals(placeId))
                .findFirst()
                .map(MockPlaceDetailsProvider::toDetails);
    }

    /** The sample places have no photos. */
    @Override
    public Optional<PlaceDetails.Photo> findPhoto(String placeId) {
        return Optional.empty();
    }

    private static PlaceDetails toDetails(Attraction a) {
        return new PlaceDetails(a.id(), a.name(), null, a.latitude(), a.longitude(), null, null, null, null, null,
                null, a.description(), null, null, null, null, "sample");
    }
}
