package com.aitravel.session.plan;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.session.config.JwtTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class TravelPlanApiIntegrationTest {

    private static final String BASE = "/api/v1/travel-plan";
    private static final String MADRID_PLAN = """
            {"baseVersion": 0, "entries": {
              "city:madrid": {"updatedAt": "2026-10-20T10:00:00Z",
                              "data": {"trip": [{"placeId": "prado"}], "itinerary": {"days": []}}},
              "favorites": {"updatedAt": "2026-10-20T09:00:00Z", "data": []},
              "city:rome": {"updatedAt": "2026-10-20T11:00:00Z", "data": null}
            }}
            """;

    @Autowired
    private MockMvc mockMvc;

    /** A fresh traveler per test, so tests never see each other's plans in the shared store. */
    private RequestPostProcessor traveler;

    @BeforeEach
    void newTraveler() {
        String subject = UUID.randomUUID().toString();
        traveler = jwt().jwt(j -> j.subject(subject));
    }

    @Test
    void anEmptyPlanStartsAtVersionZero() throws Exception {
        mockMvc.perform(get(BASE).with(traveler))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.entries").isEmpty())
                .andExpect(jsonPath("$.ownerRef", matchesPattern("[0-9a-f]{64}")));
    }

    @Test
    void savesAndReturnsThePlanIncludingDeletions() throws Exception {
        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content(MADRID_PLAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get(BASE).with(traveler))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.entries['city:madrid'].data.trip[0].placeId").value("prado"))
                .andExpect(jsonPath("$.entries['city:madrid'].updatedAt").value("2026-10-20T10:00:00Z"))
                .andExpect(jsonPath("$.entries['city:rome'].data").doesNotExist());
    }

    @Test
    void rejectsASaveFromAStaleVersion() throws Exception {
        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content(MADRID_PLAN)).andExpect(status().isOk());

        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content(MADRID_PLAN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Travel plan changed"));
    }

    @Test
    void eachTravelerOnlySeesTheirOwnPlan() throws Exception {
        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content(MADRID_PLAN)).andExpect(status().isOk());

        mockMvc.perform(get(BASE).with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.entries").isEmpty());
    }

    @Test
    void rejectsUnknownEntryKeysAndMissingTimestamps() throws Exception {
        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content("""
                        {"baseVersion": 0, "entries": {"settings": {"updatedAt": "2026-10-20T10:00:00Z", "data": {}}}}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content("""
                        {"baseVersion": 0, "entries": {"city:madrid": {"data": {}}}}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAPlanTooLargeToStore() throws Exception {
        String big = "x".repeat(TravelPlanService.MAX_PLAN_BYTES);
        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content("""
                        {"baseVersion": 0, "entries": {"city:madrid": {"updatedAt": "2026-10-20T10:00:00Z", "data": "%s"}}}
                        """.formatted(big)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.title").value("Travel plan too large"));
    }
}
