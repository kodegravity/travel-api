package com.aitravel.places.provider;

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

    /** Place Details (New) response. Only the fields in the details field mask are modelled. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaceDetailsResponse(
            String id,
            DisplayName displayName,
            String formattedAddress,
            LatLng location,
            Double rating,
            Integer userRatingCount,
            OpeningHours regularOpeningHours,
            OpeningHours currentOpeningHours,
            String websiteUri,
            String googleMapsUri,
            LocalizedText editorialSummary,
            String priceLevel,
            AccessibilityOptions accessibilityOptions,
            List<Review> reviews,
            List<Photo> photos
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpeningHours(Boolean openNow, List<String> weekdayDescriptions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalizedText(String text, String languageCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccessibilityOptions(
            Boolean wheelchairAccessibleEntrance,
            Boolean wheelchairAccessibleRestroom,
            Boolean wheelchairAccessibleSeating,
            Boolean wheelchairAccessibleParking
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Review(
            Integer rating,
            LocalizedText text,
            String relativePublishTimeDescription,
            AuthorAttribution authorAttribution
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Photo(String name, Integer widthPx, Integer heightPx, List<AuthorAttribution> authorAttributions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorAttribution(String displayName, String uri, String photoUri) {
    }

    /** Photo media response with {@code skipHttpRedirect=true}: a short-lived URL without the API key. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhotoMedia(String name, String photoUri) {
    }
}
