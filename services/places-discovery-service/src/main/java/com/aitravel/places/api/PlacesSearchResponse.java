package com.aitravel.places.api;

import com.aitravel.places.model.Attraction;
import java.util.List;

/** Response body for {@code POST /internal/v1/places/search}. An empty list means no attractions were found. */
public record PlacesSearchResponse(List<Attraction> attractions) {

    public PlacesSearchResponse {
        attractions = List.copyOf(attractions);
    }
}
