package com.aitravel.session.controller;

import com.aitravel.session.config.JwtTestSupport;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.service.RecommendationService;
import com.aitravel.session.support.MutableClock;
import com.aitravel.session.support.StubRecommendationService;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
@SpringBootTest(properties = "travel.session.max-active-sessions=50")
@AutoConfigureMockMvc
class TravelSessionApiIntegrationTest {

    private static final String BASE = "/api/v1/travel-sessions";
    private static final String NEAR_ROME = "{\"latitude\": 41.9028, \"longitude\": 12.4964}";
    // Roughly 300 m north of NEAR_ROME.
    private static final String MOVED_300M = "{\"latitude\": 41.9055, \"longitude\": 12.4964}";
    // Roughly 100 m north of NEAR_ROME.
    private static final String MOVED_100M = "{\"latitude\": 41.9037, \"longitude\": 12.4964}";
    private static final String VALID_CREATE = """
            {"interests": ["HISTORY", "ART"], "maxBudget": 50, "currencyCode": "EUR", "availableMinutes": 180}
            """;

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        RecommendationService stubRecommendationService() {
            return new StubRecommendationService();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    // Spy: the real pipeline still runs, and calls are counted to prove skipped refreshes never reach it.
    @MockitoSpyBean
    private RecommendationService recommendationService;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    private String createSession() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE).contentType("application/json").content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.sessionId");
    }

    private String refresh(String sessionId, String location) throws Exception {
        return mockMvc.perform(post(BASE + "/" + sessionId + "/recommendations")
                        .contentType("application/json")
                        .content("{\"location\": " + location + "}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createsSessionAndReturnsCreatedWithLocation() throws Exception {
        mockMvc.perform(post(BASE).contentType("application/json").content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isString())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.expiresAt").value("2026-01-01T10:30:00Z"))
                .andExpect(header().string("Location", containsString(BASE + "/")));
    }

    @Test
    void rejectsInvalidCreateRequestsWithValidationProblem() throws Exception {
        String invalid = """
                {"interests": [], "maxBudget": -1, "currencyCode": "ZZZ", "availableMinutes": 5}
                """;
        mockMvc.perform(post(BASE).contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.interests").exists())
                .andExpect(jsonPath("$.errors.maxBudget").exists())
                .andExpect(jsonPath("$.errors.currencyCode").exists())
                .andExpect(jsonPath("$.errors.availableMinutes").exists());
    }

    @Test
    void firstLocationTriggersUpdatedRecommendations() throws Exception {
        String id = createSession();

        mockMvc.perform(post(BASE + "/" + id + "/recommendations").contentType("application/json")
                        .content("{\"location\": " + NEAR_ROME + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id))
                .andExpect(jsonPath("$.refreshStatus").value("UPDATED"))
                .andExpect(jsonPath("$.recommendations.length()").value(lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.generatedAt").value("2026-01-01T10:00:00Z"));
    }

    @Test
    void smallMovementReturnsUnchanged() throws Exception {
        String id = createSession();
        refresh(id, NEAR_ROME);
        clock.advance(Duration.ofMinutes(10));

        mockMvc.perform(post(BASE + "/" + id + "/recommendations").contentType("application/json")
                        .content("{\"location\": " + MOVED_100M + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshStatus").value("UNCHANGED"))
                .andExpect(jsonPath("$.recommendations.length()").value(0));
    }

    @Test
    void movementAfterCooldownRefreshesWithDifferentPlaces() throws Exception {
        String id = createSession();
        String first = refresh(id, NEAR_ROME);
        clock.advance(Duration.ofMinutes(6));

        String second = refresh(id, MOVED_300M);

        assertThat(second).contains("\"refreshStatus\":\"UPDATED\"");
        List<String> firstIds = JsonPath.read(first, "$.recommendations[*].placeId");
        List<String> secondIds = JsonPath.read(second, "$.recommendations[*].placeId");
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
    }

    @Test
    void invalidSessionIdFormatIsBadRequest() throws Exception {
        mockMvc.perform(post(BASE + "/not-a-uuid/recommendations").contentType("application/json")
                        .content("{\"location\": " + NEAR_ROME + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid path parameter"));
    }

    @Test
    void invalidLocationIsBadRequest() throws Exception {
        String id = createSession();
        mockMvc.perform(post(BASE + "/" + id + "/recommendations").contentType("application/json")
                        .content("{\"location\": {\"latitude\": 120, \"longitude\": 12.4964}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['location.latitude']").exists());
    }

    @Test
    void unknownSessionIsNotFound() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/recommendations").contentType("application/json")
                        .content("{\"location\": " + NEAR_ROME + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Travel session not found"));
    }

    @Test
    void expiredSessionIsGone() throws Exception {
        String id = createSession();
        clock.advance(Duration.ofMinutes(31));

        mockMvc.perform(post(BASE + "/" + id + "/recommendations").contentType("application/json")
                        .content("{\"location\": " + NEAR_ROME + "}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("Travel session expired"));
    }

    @Test
    void stopReturnsNoContentAndBlocksFurtherRefreshes() throws Exception {
        String id = createSession();
        refresh(id, NEAR_ROME);

        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(post(BASE + "/" + id + "/recommendations").contentType("application/json")
                        .content("{\"location\": " + MOVED_300M + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Travel session stopped"));

        // Repeated stop is idempotent.
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
    }

    @Test
    void stopUnknownSessionIsNotFoundAndInvalidIdIsBadRequest() throws Exception {
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/bad-id")).andExpect(status().isBadRequest());
    }

    @Test
    void skippedRefreshDoesNotCallRecommendationPipeline() throws Exception {
        String id = createSession();
        refresh(id, NEAR_ROME);
        clearInvocations(recommendationService);

        refresh(id, MOVED_100M);

        verify(recommendationService, times(0)).recommend(any(RecommendationRequest.class), anySet());
    }

    @Test
    void existingRecommendationEndpointStillWorks() throws Exception {
        String body = """
                {"location": {"latitude": 41.9028, "longitude": 12.4964},
                 "interests": ["HISTORY"], "maxBudget": 50, "currencyCode": "EUR", "availableMinutes": 180}
                """;
        mockMvc.perform(post("/api/v1/recommendations").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isArray());
    }
}
