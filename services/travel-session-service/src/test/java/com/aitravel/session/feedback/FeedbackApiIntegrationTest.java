package com.aitravel.session.feedback;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class FeedbackApiIntegrationTest {

    private static final String BASE = "/api/v1/feedback";

    @Autowired
    private MockMvc mockMvc;

    /** A fresh traveler per test, so tests never see each other's feedback in the shared store. */
    private RequestPostProcessor traveler;

    @BeforeEach
    void newTraveler() {
        String subject = UUID.randomUUID().toString();
        traveler = jwt().jwt(j -> j.subject(subject));
    }

    @Test
    void ratesListsAndClearsAPlace() throws Exception {
        mockMvc.perform(get(BASE).with(traveler))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places").isEmpty());

        mockMvc.perform(put(BASE + "/ChIJ_prado-1").with(traveler).contentType("application/json")
                        .content("{\"rating\": \"DISLIKE\", \"name\": \"Prado\", \"cityId\": \"madrid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].placeId").value("ChIJ_prado-1"))
                .andExpect(jsonPath("$.places[0].rating").value("DISLIKE"))
                .andExpect(jsonPath("$.places[0].category").doesNotExist());

        mockMvc.perform(get(BASE).with(jwt().jwt(j -> j.subject("another-traveler"))))
                .andExpect(jsonPath("$.places").isEmpty());

        mockMvc.perform(delete(BASE + "/ChIJ_prado-1").with(traveler))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places").isEmpty());
    }

    @Test
    void rejectsAnUnknownRatingAndABadPlaceId() throws Exception {
        mockMvc.perform(put(BASE + "/abc").with(traveler).contentType("application/json")
                        .content("{\"rating\": \"MEH\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(BASE + "/a b").with(traveler).contentType("application/json")
                        .content("{\"rating\": \"LIKE\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(BASE + "/abc").with(traveler).contentType("application/json")
                        .content("{\"rating\": \"LIKE\", \"cityId\": \"Not A City\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cityId").exists());
    }
}
