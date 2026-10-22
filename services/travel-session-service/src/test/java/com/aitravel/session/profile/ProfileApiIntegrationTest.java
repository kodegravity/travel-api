package com.aitravel.session.profile;

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

/** The profile API against the default in-memory store. */
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProfileApiIntegrationTest {

    private static final String BASE = "/api/v1/profile";
    private static final String PROFILE = """
            {"displayName": "  Sam  ", "homeCity": "Toronto", "interests": ["FOOD", "ART"],
             "currency": "CAD", "travelMode": "transit"}
            """;

    @Autowired
    private MockMvc mockMvc;

    /** A fresh traveler per test, so tests never see each other's profiles in the shared store. */
    private RequestPostProcessor traveler;

    @BeforeEach
    void newTraveler() {
        String subject = UUID.randomUUID().toString();
        traveler = jwt().jwt(j -> j.subject(subject));
    }

    @Test
    void aNewTravelerGetsADefaultProfile() throws Exception {
        mockMvc.perform(get(BASE).with(traveler))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerRef", matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("$.displayName").doesNotExist())
                .andExpect(jsonPath("$.interests").isEmpty())
                .andExpect(jsonPath("$.travelMode").value("walk"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void savesTheProfileTrimmedAndReturnsIt() throws Exception {
        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content(PROFILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Sam"));

        mockMvc.perform(get(BASE).with(traveler))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Sam"))
                .andExpect(jsonPath("$.homeCity").value("Toronto"))
                .andExpect(jsonPath("$.interests[0]").value("FOOD"))
                .andExpect(jsonPath("$.interests[1]").value("ART"))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.travelMode").value("transit"));
    }

    @Test
    void blankTextClearsAField() throws Exception {
        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content(PROFILE)).andExpect(status().isOk());

        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content("""
                        {"displayName": " ", "homeCity": "", "interests": [], "currency": "", "travelMode": "walk"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").doesNotExist())
                .andExpect(jsonPath("$.homeCity").doesNotExist())
                .andExpect(jsonPath("$.currency").doesNotExist());
    }

    @Test
    void eachTravelerOnlySeesTheirOwnProfile() throws Exception {
        mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content(PROFILE)).andExpect(status().isOk());

        mockMvc.perform(get(BASE).with(jwt().jwt(j -> j.subject("someone-else-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").doesNotExist());
    }

    @Test
    void rejectsInvalidFields() throws Exception {
        for (String body : new String[] {
                PROFILE.replace("transit", "teleport"),
                PROFILE.replace("CAD", "XYZ"),
                PROFILE.replace("FOOD", "SKYDIVING"),
                PROFILE.replace("  Sam  ", "x".repeat(61)),
                PROFILE.replace("  Sam  ", "Sam\\u0007"),
                "{\"displayName\": \"Sam\"}",
        }) {
            mockMvc.perform(put(BASE).with(traveler).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
