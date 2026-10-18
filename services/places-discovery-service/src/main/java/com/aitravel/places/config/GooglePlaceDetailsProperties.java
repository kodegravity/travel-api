package com.aitravel.places.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Google Place Details (New) settings, bound from {@code google.places.details.*}.
 *
 * <p>The field mask includes reviews, so each call is billed at the Enterprise + Atmosphere rate, and each photo is a
 * separate Place Photos call. At 30 a day both stay inside Google's free 1,000 a month.
 *
 * @param maxCallsPerDay hard cap on detail lookups per UTC day per task; each lookup also fetches at most one photo
 * @param fieldMask      value of the {@code X-Goog-FieldMask} header; every field here is billed
 * @param photoMaxWidthPx width requested for the photo
 * @param maxReviews     reviews passed on (Google returns at most 5)
 */
@Validated
@ConfigurationProperties(prefix = "google.places.details")
public record GooglePlaceDetailsProperties(
        @DefaultValue("30") @PositiveOrZero int maxCallsPerDay,
        @DefaultValue("id,displayName,formattedAddress,location,rating,userRatingCount,regularOpeningHours.weekdayDescriptions,"
                + "currentOpeningHours.openNow,websiteUri,googleMapsUri,editorialSummary,priceLevel,accessibilityOptions,"
                + "reviews,photos") String fieldMask,
        @DefaultValue("1200") @Min(100) @Max(4800) int photoMaxWidthPx,
        @DefaultValue("5") @Min(0) @Max(5) int maxReviews
) {
}
