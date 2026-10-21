package com.aitravel.session.controller;

import com.aitravel.session.config.JwtTestSupport;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTest {

    private static final String ALLOWED = "http://localhost:5173";
    private static final String DISALLOWED = "https://evil.example.com";
    private static final String CREATE_BODY =
            "{\"interests\":[\"HISTORY\"],\"maxBudget\":50,\"currencyCode\":\"EUR\",\"availableMinutes\":120}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowedOriginReceivesAccessControlHeader() throws Exception {
        mockMvc.perform(post("/api/v1/travel-sessions")
                        .header("Origin", ALLOWED)
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
    }

    @Test
    void disallowedOriginDoesNotReceiveAccessControlHeader() throws Exception {
        mockMvc.perform(post("/api/v1/travel-sessions")
                        .header("Origin", DISALLOWED)
                        .contentType("application/json")
                        .content(CREATE_BODY))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void successfulPreflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/travel-sessions")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED))
                .andExpect(header().string("Access-Control-Allow-Methods", containsStringIgnoringCase("POST")))
                .andExpect(header().string("Access-Control-Allow-Methods", containsStringIgnoringCase("PUT")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("content-type")));
    }

    @Test
    void preflightAllowsBearerTokenHeader() throws Exception {
        mockMvc.perform(options("/api/v1/travel-sessions")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("content-type")));
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/travel-sessions")
                        .header("Origin", DISALLOWED)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsIsNotAppliedToActuatorEndpoints() throws Exception {
        mockMvc.perform(options("/actuator/health")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
