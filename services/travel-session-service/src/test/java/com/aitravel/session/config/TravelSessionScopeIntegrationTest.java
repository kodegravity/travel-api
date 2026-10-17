package com.aitravel.session.config;

import static com.aitravel.session.config.JwtTestSupport.ALICE;
import static com.aitravel.session.config.JwtTestSupport.AUDIENCE_PROPERTY;
import static com.aitravel.session.config.JwtTestSupport.ISSUER_PROPERTY;
import static com.aitravel.session.config.JwtTestSupport.bearer;
import static com.aitravel.session.config.JwtTestSupport.validToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        ISSUER_PROPERTY,
        AUDIENCE_PROPERTY,
        "travel.security.required-scope=travel.sessions"
})
@Import(JwtTestSupport.TrustedKeyDecoderConfig.class)
class TravelSessionScopeIntegrationTest {

    private static final String SESSIONS = "/api/v1/travel-sessions";
    private static final String CREATE_BODY = """
            {"interests":["HISTORY"],"maxBudget":50,"currencyCode":"EUR","availableMinutes":180}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tokenWithoutRequiredScopeIsForbidden() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(ALICE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithRequiredScopeCanCreateSession() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(ALICE, "travel.sessions")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());
    }
}
