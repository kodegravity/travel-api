package com.aitravel.places.provider;

import com.aitravel.places.model.Attraction;
import com.aitravel.places.model.Interest;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory development provider with a fixed set of Rome attractions.
 *
 * <p>All costs and durations are <b>mock development values</b>, not verified prices or opening hours.
 * Coordinates are real. The provider ignores the search location and returns every attraction;
 * distance filtering happens in the calling service.
 */
@Component
@ConditionalOnProperty(prefix = "attraction", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAttractionProvider implements AttractionProvider {

    private static final Logger log = LoggerFactory.getLogger(MockAttractionProvider.class);
    private static final List<Attraction> ATTRACTIONS = List.of(
            attraction("colosseum", "Colosseum", Set.of(Interest.HISTORY),
                    "41.8902", "12.4922", "16.00", 120,
                    "Ancient amphitheatre in the centre of Rome."),
            attraction("roman-forum", "Roman Forum and Palatine Hill", Set.of(Interest.HISTORY),
                    "41.8925", "12.4853", "16.00", 120,
                    "Ruins of the ancient civic centre of Rome."),
            attraction("pantheon", "Pantheon", Set.of(Interest.HISTORY, Interest.ART),
                    "41.8986", "12.4769", "5.00", 45,
                    "Well-preserved Roman temple with a famous dome."),
            attraction("trevi-fountain", "Trevi Fountain", Set.of(Interest.ART, Interest.LOCAL_EXPERIENCES),
                    "41.9009", "12.4833", "0.00", 20,
                    "Baroque fountain in the historic centre."),
            attraction("spanish-steps", "Spanish Steps", Set.of(Interest.ART, Interest.SHOPPING),
                    "41.9060", "12.4828", "0.00", 20,
                    "Monumental staircase near luxury shopping streets."),
            attraction("vatican-museums", "Vatican Museums", Set.of(Interest.ART, Interest.HISTORY),
                    "41.9065", "12.4536", "20.00", 180,
                    "Museum complex with classical sculpture and the Sistine Chapel."),
            attraction("campo-de-fiori", "Campo de' Fiori Market", Set.of(Interest.FOOD, Interest.LOCAL_EXPERIENCES),
                    "41.8956", "12.4723", "0.00", 60,
                    "Morning market square with local food stalls."),
            attraction("trastevere-food-walk", "Trastevere Food Walk", Set.of(Interest.FOOD, Interest.LOCAL_EXPERIENCES),
                    "41.8890", "12.4698", "35.00", 150,
                    "Guided tasting tour through the Trastevere neighbourhood."),
            attraction("villa-borghese", "Villa Borghese Gardens", Set.of(Interest.NATURE),
                    "41.9142", "12.4923", "0.00", 90,
                    "Large public park with walking paths and lakes."),
            attraction("testaccio-market", "Testaccio Market", Set.of(Interest.FOOD),
                    "41.8765", "12.4770", "15.00", 75,
                    "Traditional Roman food market.")
    );

    /** The fixed sample set, shared with {@link MockPlaceDetailsProvider}. */
    static List<Attraction> sampleAttractions() {
        return ATTRACTIONS;
    }

    @Override
    public List<Attraction> findNearby(BigDecimal latitude, BigDecimal longitude, double searchRadiusKm) {
        log.info("Mock provider returning {} attractions", ATTRACTIONS.size());
        return ATTRACTIONS;
    }

    private static Attraction attraction(String id, String name, Set<Interest> categories,
                                         String latitude, String longitude,
                                         String estimatedCost, int durationMinutes, String description) {
        return new Attraction(
                id,
                name,
                categories,
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                new BigDecimal(estimatedCost),
                "EUR",
                durationMinutes,
                description);
    }
}
