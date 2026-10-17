package com.aitravel.recommendation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.recommendation.exception.AttractionProviderException;
import com.aitravel.recommendation.provider.AttractionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InternalRecommendationControllerTest {

    private static final String VALID_BODY = """
            {"request": {"location": {"latitude": 41.9028, "longitude": 12.4964},
              "interests": ["HISTORY"], "maxBudget": 50, "currencyCode": "EUR", "availableMinutes": 180},
             "excludedPlaceIds": ["seen-1"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttractionProvider attractionProvider;

    @Test
    void validRequestReturnsRecommendationsAndHonorsExclusions() throws Exception {
        when(attractionProvider.findNearby(any(), any())).thenReturn(java.util.List.of());

        mockMvc.perform(post("/internal/v1/recommendations").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isArray());
    }

    @Test
    void invalidCoordinatesReturnBadRequest() throws Exception {
        String body = VALID_BODY.replace("41.9028", "123.0");

        mockMvc.perform(post("/internal/v1/recommendations").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['request.location.latitude']").exists());
    }

    @Test
    void providerFailureReturnsServiceUnavailable() throws Exception {
        when(attractionProvider.findNearby(any(), any()))
                .thenThrow(new AttractionProviderException("upstream down", null));

        mockMvc.perform(post("/internal/v1/recommendations").contentType("application/json").content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Attraction provider unavailable"));
    }
}
