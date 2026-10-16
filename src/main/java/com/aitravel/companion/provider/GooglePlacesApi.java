package com.aitravel.companion.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Typed request and response bodies for Google Places API (New) {@code places:searchNearby}.
 * Only the fields used by this application are modelled.
 */
public final class GooglePlacesApi {

    private GooglePlacesApi() {
    }

    public record SearchNearbyRequest(
            List<String> includedTypes,
            int maxResultCount,
            String rankPreference,
            LocationRestriction locationRestriction
    ) {
    }

    public record LocationRestriction(Circle circle) {
    }

    /** Radius is in metres. */
    public record Circle(LatLng center, double radius) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchNearbyResponse(List<Place> places) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(
            String id,
            DisplayName displayName,
            LatLng location,
            List<String> types,
            String googleMapsUri
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DisplayName(String text, String languageCode) {
    }

    public record LatLng(double latitude, double longitude) {
    }
}
