package com.aitravel.companion.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Google Places (New) settings, bound from {@code google.places.*}.
 *
 * <p>The API key is supplied through the {@code GOOGLE_PLACES_API_KEY} environment variable. It is
 * masked in {@link #toString()} so it cannot leak through logs or diagnostics.
 *
 * @param apiKey         API key sent in the {@code X-Goog-Api-Key} header
 * @param baseUrl        Google Places API base URL
 * @param connectTimeout connection timeout for Google requests
 * @param readTimeout    response timeout for Google requests
 * @param maxResultCount places requested per search (Google allows at most 20)
 * @param fieldMask      value of the {@code X-Goog-FieldMask} header; lists only the fields that are needed
 */
@Validated
@ConfigurationProperties(prefix = "google.places")
public record GooglePlacesProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://places.googleapis.com") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("20") @Min(1) @Max(20) int maxResultCount,
        @DefaultValue("places.id,places.displayName,places.location,places.types,places.googleMapsUri") String fieldMask
) {

    @Override
    public String toString() {
        return "GooglePlacesProperties[baseUrl=" + baseUrl
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", maxResultCount=" + maxResultCount
                + ", fieldMask=" + fieldMask
                + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<unset>" : "<redacted>") + "]";
    }
}
