package com.aitravel.recommendation.chat;

import com.aitravel.recommendation.dto.LocationRequest;
import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

final class ChatFixtures {

    static final BigDecimal LAT = new BigDecimal("40.4169");
    static final BigDecimal LON = new BigDecimal("-3.7035");

    static final Attraction PRADO = place("prado", "Prado Museum", Set.of(Interest.ART), "40.4138", "-3.6921", 120);
    static final Attraction RETIRO = place("retiro", "El Retiro Park", Set.of(Interest.NATURE), "40.4153", "-3.6844", 90);
    static final Attraction MARKET = place("market", "Mercado de San Miguel", Set.of(Interest.FOOD), "40.4154", "-3.7090", 45);
    static final Attraction FAR = place("far", "Far Palace", Set.of(Interest.HISTORY), "40.6000", "-3.7000", 60);

    static final ChatProperties PROPERTIES = new ChatProperties(200, 15, 3, Duration.ofMinutes(15), "https://api.open-meteo.com");

    private ChatFixtures() {
    }

    static Attraction place(String id, String name, Set<Interest> categories, String lat, String lon, int minutes) {
        return new Attraction(id, name, categories, new BigDecimal(lat), new BigDecimal(lon), null, null, minutes, null);
    }

    static ChatRequest request(String message, boolean withLocation, Set<Interest> interests) {
        return new ChatRequest(
                List.of(new ChatRequest.Turn(ChatRequest.Role.USER, message)),
                new ChatRequest.Context(withLocation ? new LocationRequest(LAT, LON) : null, "Madrid", "2026-10-19T15:30",
                        interests, new BigDecimal("40"), "EUR", 180, List.of("Royal Palace")));
    }
}
