package com.aitravel.places.provider;

import com.aitravel.places.model.PlaceDetails;
import java.util.Optional;

/** Looks up one place by the id a search returned. Empty when the provider does not know the place. */
public interface PlaceDetailsProvider {

    Optional<PlaceDetails> findDetails(String placeId);
}
