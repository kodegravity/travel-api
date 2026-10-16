package com.aitravel.companion.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.companion.dto.RecommendationDto;
import com.aitravel.companion.dto.RecommendationResponse;
import com.aitravel.companion.exception.AttractionProviderException;
import com.aitravel.companion.service.RecommendationService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class ApiFailureIntegrationTest {

    private static final String BASE = "/api/v1/travel-sessions";
    private static final String CREATE_BODY =
            "{\"interests\":[\"HISTORY\"],\"maxBudget\":50,\"currencyCode\":\"EUR\",\"availableMinutes\":120}";
    private static final String NEAR_ROME = "{\"location\":{\"latitude\":41.9028,\"longitude\":12.4964}}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void providerFailureReturnsServiceUnavailableAndLeavesSessionRetryable() throws Exception {
        String sessionId = createSession();
        doThrow(new AttractionProviderException("upstream 500 with internal details", null))
                .when(recommendationService).recommend(any(), anySet());

        mockMvc.perform(refresh(sessionId, NEAR_ROME))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Attraction provider unavailable"))
                .andExpect(content().string(not(containsString("internal details"))));

        // The failed evaluation must not count as the baseline, so the same location is evaluated again.
        RecommendationDto dto = new RecommendationDto("pantheon", "Pantheon", "HISTORY", "desc",
                BigDecimal.valueOf(41.8986), BigDecimal.valueOf(12.4769), 45, BigDecimal.valueOf(5),
                "EUR", "https://www.google.com/maps/dir/");
        doReturn(new RecommendationResponse(List.of(dto)))
                .when(recommendationService).recommend(any(), anySet());

        mockMvc.perform(refresh(sessionId, NEAR_ROME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshStatus").value("UPDATED"))
                .andExpect(jsonPath("$.recommendations[0].placeId").value("pantheon"));
    }

    @Test
    void successfulEvaluationWithNoUnseenAttractionsReturnsNoNewResults() throws Exception {
        String sessionId = createSession();
        doReturn(new RecommendationResponse(List.of()))
                .when(recommendationService).recommend(any(), anySet());

        mockMvc.perform(refresh(sessionId, NEAR_ROME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshStatus").value("NO_NEW_RESULTS"))
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    void unexpectedErrorReturnsGenericInternalServerError() throws Exception {
        doThrow(new IllegalStateException("secret internal detail"))
                .when(recommendationService).recommend(any());

        mockMvc.perform(post("/api/v1/recommendations")
                        .contentType("application/json")
                        .content("{\"location\":{\"latitude\":41.9028,\"longitude\":12.4964},"
                                + "\"interests\":[\"HISTORY\"],\"maxBudget\":50,"
                                + "\"currencyCode\":\"EUR\",\"availableMinutes\":120}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    private String createSession() throws Exception {
        String body = mockMvc.perform(post(BASE).contentType("application/json").content(CREATE_BODY))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.sessionId");
    }

    private RequestBuilder refresh(String sessionId, String json) {
        return post(BASE + "/" + sessionId + "/recommendations").contentType("application/json").content(json);
    }
}
