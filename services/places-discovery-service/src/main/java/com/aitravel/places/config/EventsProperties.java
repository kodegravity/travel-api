package com.aitravel.places.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Live event settings, bound from {@code events.*}.
 *
 * <p>The Ticketmaster key is supplied through {@code TICKETMASTER_API_KEY} and masked in {@link #toString()}. The
 * Discovery API is free up to 5,000 calls a day; the daily cap here keeps a loop or a bug far below that.
 *
 * @param provider       "mock" (sample events, no key), "ticketmaster", or "none"
 * @param apiKey         Ticketmaster Discovery API consumer key
 * @param baseUrl        Ticketmaster API base URL
 * @param connectTimeout connection timeout
 * @param readTimeout    response timeout
 * @param pageSize       events requested per search (Ticketmaster allows up to 200)
 * @param maxCallsPerDay hard cap on Ticketmaster calls per UTC day per task
 */
@Validated
@ConfigurationProperties(prefix = "events")
public record EventsProperties(
        @DefaultValue("mock") String provider,
        @DefaultValue("") String apiKey,
        @DefaultValue("https://app.ticketmaster.com") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("30") @Min(1) @Max(200) int pageSize,
        @DefaultValue("1000") @PositiveOrZero int maxCallsPerDay
) {

    @Override
    public String toString() {
        return "EventsProperties[provider=" + provider + ", baseUrl=" + baseUrl + ", pageSize=" + pageSize
                + ", maxCallsPerDay=" + maxCallsPerDay
                + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<unset>" : "<redacted>") + "]";
    }
}
