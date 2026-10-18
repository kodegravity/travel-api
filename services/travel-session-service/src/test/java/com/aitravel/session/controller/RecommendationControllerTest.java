package com.aitravel.session.controller;

import com.aitravel.session.config.JwtTestSupport;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

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

@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
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

    @Test
    void defaultPageReturnsEveryResultWithPagingInfo() throws Exception {
        givenRecommendations(25);

        postJson(VALID_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void pageAndSizeSliceTheRankedList() throws Exception {
        givenRecommendations(7);

        postJsonPaged(VALID_REQUEST, "page=1&size=3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()").value(3))
                .andExpect(jsonPath("$.recommendations[0].placeId").value("place-3"))
                .andExpect(jsonPath("$.recommendations[2].placeId").value("place-5"))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void lastPageMayBeShort() throws Exception {
        givenRecommendations(7);

        postJsonPaged(VALID_REQUEST, "page=2&size=3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()").value(1))
                .andExpect(jsonPath("$.recommendations[0].placeId").value("place-6"));
    }

    @Test
    void pageBeyondTheEndIsEmptyButKeepsTotals() throws Exception {
        givenRecommendations(4);

        postJsonPaged(VALID_REQUEST, "page=5&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void noResultsReportZeroPages() throws Exception {
        givenRecommendations(0);

        postJson(VALID_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void negativePageReturnsBadRequest() throws Exception {
        postJsonPaged(VALID_REQUEST, "page=-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasKey("page")));
    }

    @Test
    void sizeOutsideRangeReturnsBadRequest() throws Exception {
        postJsonPaged(VALID_REQUEST, "size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("size")));
        postJsonPaged(VALID_REQUEST, "size=51").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("size")));
    }

    @Test
    void nonNumericPageReturnsBadRequest() throws Exception {
        postJsonPaged(VALID_REQUEST, "page=abc").andExpect(status().isBadRequest());
    }

    private void givenRecommendations(int count) {
        List<RecommendationDto> items = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new RecommendationDto("place-" + i, "Place " + i, "HISTORY", null,
                        new BigDecimal("41.89"), new BigDecimal("12.49"), 60, null, null, "https://maps.example/" + i))
                .toList();
        given(recommendationService.recommend(any(RecommendationRequest.class)))
                .willReturn(new RecommendationResponse(items));
    }

    private ResultActions postJsonPaged(String body, String query) throws Exception {
        return mockMvc.perform(post("/api/v1/recommendations?" + query)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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
