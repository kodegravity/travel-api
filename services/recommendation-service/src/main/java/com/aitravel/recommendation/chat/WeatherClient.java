package com.aitravel.recommendation.chat;

import com.aitravel.recommendation.service.SharedStore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Current weather and the next few hours of rain chance from Open-Meteo (free, no key). Best effort: any failure
 * means no weather, and the chat carries on without it. Cached per ~100 m area.
 */
@Component
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);
    private static final int RAIN_LIKELY_PCT = 50;
    private static final int HOURS_AHEAD = 4;

    private static final String SHARED_PREFIX = "roam:weather:";

    private final RestClient restClient;
    private final AreaCache<Optional<ChatResponse.Weather>> cache;
    private final SharedStore shared;
    private final JsonMapper mapper;
    private final Duration ttl;

    @Autowired
    public WeatherClient(ChatProperties properties, SharedStore shared, JsonMapper mapper) {
        this(RestClient.builder()
                .baseUrl(properties.weatherBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withConnectTimeout(java.time.Duration.ofSeconds(2))
                        .withReadTimeout(java.time.Duration.ofSeconds(3))))
                .build(), properties, Clock.systemUTC(), shared, mapper);
    }

    WeatherClient(RestClient restClient, ChatProperties properties, Clock clock) {
        this(restClient, properties, clock, SharedStore.NONE, JsonMapper.builder().build());
    }

    WeatherClient(RestClient restClient, ChatProperties properties, Clock clock, SharedStore shared, JsonMapper mapper) {
        this.restClient = restClient;
        this.cache = new AreaCache<>(properties.cacheTtl(), clock);
        this.shared = shared;
        this.mapper = mapper;
        this.ttl = properties.cacheTtl();
    }

    /** The area's weather: from Redis when another task fetched it recently, else this task's cache or Open-Meteo. */
    public Optional<ChatResponse.Weather> current(BigDecimal latitude, BigDecimal longitude) {
        String area = AreaCache.key(latitude, longitude);
        Optional<ChatResponse.Weather> cached = shared.get(SHARED_PREFIX + area).flatMap(this::read);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ChatResponse.Weather> weather = cache.get(area, () -> fetch(latitude, longitude));
        weather.ifPresent(value -> shared.put(SHARED_PREFIX + area, mapper.writeValueAsString(value), ttl));
        return weather;
    }

    private Optional<ChatResponse.Weather> read(String json) {
        try {
            return Optional.of(mapper.readValue(json, ChatResponse.Weather.class));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<ChatResponse.Weather> fetch(BigDecimal latitude, BigDecimal longitude) {
        try {
            Forecast forecast = restClient.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", latitude.setScale(3, java.math.RoundingMode.HALF_UP))
                            .queryParam("longitude", longitude.setScale(3, java.math.RoundingMode.HALF_UP))
                            .queryParam("current", "temperature_2m,weather_code,is_day")
                            .queryParam("hourly", "precipitation_probability")
                            .queryParam("forecast_hours", HOURS_AHEAD)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(Forecast.class);
            if (forecast == null || forecast.current() == null) {
                return Optional.empty();
            }
            Integer rainChance = forecast.hourly() == null || forecast.hourly().precipitationProbability() == null
                    ? null
                    : forecast.hourly().precipitationProbability().stream()
                            .filter(java.util.Objects::nonNull).max(Integer::compare).orElse(null);
            int code = forecast.current().weatherCode() == null ? -1 : forecast.current().weatherCode();
            boolean wetNow = isWet(code);
            return Optional.of(new ChatResponse.Weather(
                    describe(code),
                    forecast.current().temperature(),
                    wetNow || (rainChance != null && rainChance >= RAIN_LIKELY_PCT),
                    rainChance,
                    forecast.current().isDay() == null || forecast.current().isDay() == 1));
        } catch (RestClientException ex) {
            log.info("Weather unavailable ({}), chat continues without it", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static boolean isWet(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
    }

    /** WMO weather interpretation codes, as Open-Meteo reports them. */
    static String describe(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55, 56, 57 -> "Drizzle";
            case 61, 63, 66 -> "Rain";
            case 65, 67 -> "Heavy rain";
            case 71, 73, 75, 77, 85, 86 -> "Snow";
            case 80, 81 -> "Rain showers";
            case 82 -> "Heavy showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Unknown";
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Forecast(Current current, Hourly hourly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Current(@JsonProperty("temperature_2m") Double temperature, @JsonProperty("weather_code") Integer weatherCode,
                   @JsonProperty("is_day") Integer isDay) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hourly(@JsonProperty("precipitation_probability") List<Integer> precipitationProbability) {
    }
}
