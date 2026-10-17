package com.aitravel.session.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.session.dto.RecommendationDto;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.service.RecommendationService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    private static final String VALID_REQUEST = """
            {
              "location": { "latitude": 48.8584, "longitude": 2.2945 },
              "interests": ["HISTORY", "FOOD"],
              "maxBudget": 50.00,
              "currencyCode": "EUR",
              "availableMinutes": 120
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void validRequestReturnsOkWithRecommendations() throws Exception {
        RecommendationDto dto = new RecommendationDto("colosseum", "Colosseum", "HISTORY",
                "Matches your interests: HISTORY.", new BigDecimal("41.8902"), new BigDecimal("12.4922"),
                120, new BigDecimal("16.00"), "EUR", "https://www.google.com/maps/dir/?api=1");
        given(recommendationService.recommend(any(RecommendationRequest.class)))
                .willReturn(new RecommendationResponse(List.of(dto)));

        postJson(VALID_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].placeId").value("colosseum"))
                .andExpect(jsonPath("$.recommendations[0].estimatedCost").value(16.00))
                .andExpect(jsonPath("$.recommendations[0].mapsUrl").isNotEmpty());
        verify(recommendationService).recommend(any(RecommendationRequest.class));
    }

    @Test
    void validRequestWithNoMatchesReturnsOkWithEmptyList() throws Exception {
        given(recommendationService.recommend(any(RecommendationRequest.class)))
                .willReturn(new RecommendationResponse(List.of()));

        postJson(VALID_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    void missingLocationReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replaceAll("\"location\": \\{[^}]*},", "");
        assertValidationError(body, "location");
    }

    @Test
    void invalidLatitudeReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replace("48.8584", "91");
        assertValidationError(body, "location.latitude");
    }

    @Test
    void invalidLongitudeReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replace("2.2945", "-181");
        assertValidationError(body, "location.longitude");
    }

    @Test
    void emptyInterestsReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replace("[\"HISTORY\", \"FOOD\"]", "[]");
        assertValidationError(body, "interests");
    }

    @Test
    void unknownInterestReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replace("\"FOOD\"", "\"SKIING\"");
        postJson(body).andExpect(status().isBadRequest());
    }

    @Test
    void negativeBudgetReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replace("50.00", "-1");
        assertValidationError(body, "maxBudget");
    }

    @Test
    void invalidCurrencyReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replace("\"EUR\"", "\"XYZ\"");
        assertValidationError(body, "currencyCode");
    }

    @Test
    void availableMinutesBelowRangeReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replace("120", "10");
        assertValidationError(body, "availableMinutes");
    }

    @Test
    void availableMinutesAboveRangeReturnsBadRequest() throws Exception {
        String body = VALID_REQUEST.replace("120", "721");
        assertValidationError(body, "availableMinutes");
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        postJson("{ \"location\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("com.fasterxml"))));
    }

    private void assertValidationError(String body, String field) throws Exception {
        postJson(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasKey(field)));
    }

    private ResultActions postJson(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/recommendations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
