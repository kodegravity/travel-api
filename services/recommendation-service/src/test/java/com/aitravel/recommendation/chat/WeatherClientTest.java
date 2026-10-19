package com.aitravel.recommendation.chat;

import static com.aitravel.recommendation.chat.ChatFixtures.LAT;
import static com.aitravel.recommendation.chat.ChatFixtures.LON;
import static com.aitravel.recommendation.chat.ChatFixtures.PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Clock;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WeatherClientTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.open-meteo.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final WeatherClient client = new WeatherClient(builder.build(), PROPERTIES, Clock.systemUTC());

    @Test
    void readsCurrentWeatherAndRainChanceAndCachesTheArea() {
        server.expect(once(), requestTo(Matchers.startsWith("https://api.open-meteo.com/v1/forecast")))
                .andExpect(queryParam("latitude", "40.417"))
                .andRespond(withSuccess("""
                        {"current": {"temperature_2m": 14.2, "weather_code": 3, "is_day": 1},
                         "hourly": {"precipitation_probability": [10, 35, 70, 40]}}
                        """, MediaType.APPLICATION_JSON));

        var weather = client.current(LAT, LON).orElseThrow();
        client.current(LAT.add(new BigDecimal("0.0002")), LON);

        assertThat(weather.summary()).isEqualTo("Overcast");
        assertThat(weather.temperatureC()).isEqualTo(14.2);
        assertThat(weather.rainLikely()).isTrue();
        assertThat(weather.rainChancePct()).isEqualTo(70);
        server.verify();
    }

    @Test
    void aFailureMeansNoWeather() {
        server.expect(requestTo(Matchers.startsWith("https://api.open-meteo.com"))).andRespond(withServerError());

        assertThat(client.current(LAT, LON)).isEmpty();
    }

    @Test
    void describesWeatherCodes() {
        assertThat(WeatherClient.describe(0)).isEqualTo("Clear sky");
        assertThat(WeatherClient.describe(63)).isEqualTo("Rain");
        assertThat(WeatherClient.isWet(81)).isTrue();
        assertThat(WeatherClient.isWet(2)).isFalse();
    }
}
