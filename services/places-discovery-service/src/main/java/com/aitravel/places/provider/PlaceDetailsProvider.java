package com.aitravel.places.provider;

import com.aitravel.places.model.PlaceDetails;
import java.util.Optional;

/** Looks up one place by the id a search returned. Empty when the provider does not know the place. */
public interface PlaceDetailsProvider {

    Optional<PlaceDetails> findDetails(String placeId);

    /** One photo for a place card. Empty when the place has no photo or the provider has none. */
    Optional<PlaceDetails.Photo> findPhoto(String placeId);
}
