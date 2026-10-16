package com.aitravel.companion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationEndpointIntegrationTest {

    private static final String ROME_CENTRE_REQUEST = """
            {
              "location": { "latitude": 41.8902, "longitude": 12.4922 },
              "interests": ["HISTORY"],
              "maxBudget": 50.00,
              "currencyCode": "EUR",
              "availableMinutes": 180
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsMockRecommendationsForRomeTraveler() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/recommendations")
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
        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ROME_CENTRE_REQUEST.replace("41.8902", "91")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['location.latitude']").exists());
    }
}
