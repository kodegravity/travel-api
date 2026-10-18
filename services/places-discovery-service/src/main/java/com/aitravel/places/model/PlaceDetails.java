package com.aitravel.places.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/**
 * Details for one place. Every field except the id and name may be missing, because providers know different
 * things; missing fields are left out of the JSON rather than guessed.
 *
 * <p>Photo and review attributions must be shown wherever the photo or review is shown (Google Maps Platform terms).
 *
 * @param priceLevel Google's level, for example {@code PRICE_LEVEL_MODERATE}
 * @param weekdayHours one line per day, as the provider formats it, for example "Monday: 10:00 AM – 6:00 PM"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceDetails(
        String placeId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Double rating,
        Integer userRatingCount,
        Boolean openNow,
        List<String> weekdayHours,
        String websiteUri,
        String googleMapsUri,
        String summary,
        String priceLevel,
        Accessibility accessibility,
        Photo photo,
        List<Review> reviews,
        /** Who supplied the data, for the attribution line: "google" or "sample". */
        String source
) {

    public PlaceDetails {
        weekdayHours = weekdayHours == null ? null : List.copyOf(weekdayHours);
        reviews = reviews == null ? null : List.copyOf(reviews);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Accessibility(
            Boolean wheelchairAccessibleEntrance,
            Boolean wheelchairAccessibleRestroom,
            Boolean wheelchairAccessibleSeating,
            Boolean wheelchairAccessibleParking
    ) {
    }

    /** A short-lived image URL that is safe to hand to the browser: it carries no API key. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Photo(String url, Integer widthPx, Integer heightPx, List<Attribution> attributions) {
        public Photo {
            attributions = attributions == null ? List.of() : List.copyOf(attributions);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attribution(String displayName, String uri) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Review(String authorName, String authorUri, Integer rating, String text, String relativeTime) {
    }
}
