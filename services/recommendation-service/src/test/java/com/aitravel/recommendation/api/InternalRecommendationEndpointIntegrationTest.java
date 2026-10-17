package com.aitravel.recommendation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import com.aitravel.recommendation.provider.AttractionProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.aitravel.recommendation.config.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the full ranking pipeline through the internal endpoint. The Places Discovery call is replaced
 * by the same Rome fixtures the monolith used for its mock provider, so results match the original behaviour.
 */
@SpringBootTest
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
@AutoConfigureMockMvc
class InternalRecommendationEndpointIntegrationTest {

    private static final String ROME_CENTRE_REQUEST = """
            {
              "request": {
                "location": { "latitude": 41.8902, "longitude": 12.4922 },
                "interests": ["HISTORY"],
                "maxBudget": 50.00,
                "currencyCode": "EUR",
                "availableMinutes": 180
              },
              "excludedPlaceIds": []
            }
            """;

    @MockitoBean
    private AttractionProvider attractionProvider;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsRankedRecommendationsForRomeTraveler() throws Exception {
        given(attractionProvider.findNearby(any(), any())).willReturn(romeAttractions());

        MvcResult result = mockMvc.perform(post("/internal/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROME_CENTRE_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()").value(3))
                .andReturn();

        JsonNode recommendations = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("recommendations");
        List<String> ids = new ArrayList<>();
        recommendations.forEach(node -> ids.add(node.get("placeId").asText()));

        // Pantheon, Colosseum and Roman Forum are within 5 km, match HISTORY, fit budget and time.
        // Vatican Museums is excluded because walking time plus its 180-minute visit exceeds 180 minutes.
        assertThat(ids).containsExactlyInAnyOrder("colosseum", "roman-forum", "pantheon");
        recommendations.forEach(node -> assertThat(node.get("mapsUrl").asText())
                .startsWith("https://www.google.com/maps/dir/?api=1")
                .contains("destination="));
    }

    @Test
    void invalidRequestStillReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/internal/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROME_CENTRE_REQUEST.replace("41.8902", "91")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['request.location.latitude']").exists());
    }

    private static List<Attraction> romeAttractions() {
        return List.of(
                attraction("colosseum", "Colosseum", Set.of(Interest.HISTORY), "41.8902", "12.4922", "16.00", 120),
                attraction("roman-forum", "Roman Forum and Palatine Hill", Set.of(Interest.HISTORY),
                        "41.8925", "12.4853", "16.00", 120),
                attraction("pantheon", "Pantheon", Set.of(Interest.HISTORY, Interest.ART),
                        "41.8986", "12.4769", "5.00", 45),
                attraction("trevi-fountain", "Trevi Fountain", Set.of(Interest.ART, Interest.LOCAL_EXPERIENCES),
                        "41.9009", "12.4833", "0.00", 20),
                attraction("spanish-steps", "Spanish Steps", Set.of(Interest.ART, Interest.SHOPPING),
                        "41.9060", "12.4828", "0.00", 20),
                attraction("vatican-museums", "Vatican Museums", Set.of(Interest.ART, Interest.HISTORY),
                        "41.9065", "12.4536", "20.00", 180),
                attraction("campo-de-fiori", "Campo de' Fiori Market", Set.of(Interest.FOOD, Interest.LOCAL_EXPERIENCES),
                        "41.8956", "12.4723", "0.00", 60),
                attraction("trastevere-food-walk", "Trastevere Food Walk", Set.of(Interest.FOOD, Interest.LOCAL_EXPERIENCES),
                        "41.8890", "12.4698", "35.00", 150),
                attraction("villa-borghese", "Villa Borghese Gardens", Set.of(Interest.NATURE),
                        "41.9142", "12.4923", "0.00", 90),
                attraction("testaccio-market", "Testaccio Market", Set.of(Interest.FOOD),
                        "41.8765", "12.4770", "15.00", 75));
    }

    private static Attraction attraction(String id, String name, Set<Interest> categories,
                                         String latitude, String longitude,
                                         String estimatedCost, int durationMinutes) {
        return new Attraction(id, name, categories, new BigDecimal(latitude), new BigDecimal(longitude),
                new BigDecimal(estimatedCost), "EUR", durationMinutes, name);
    }
}
