package com.aitravel.session.config;

import static com.aitravel.session.config.JwtTestSupport.ALICE;
import static com.aitravel.session.config.JwtTestSupport.BOB;
import static com.aitravel.session.config.JwtTestSupport.AUDIENCE_PROPERTY;
import static com.aitravel.session.config.JwtTestSupport.ISSUER_PROPERTY;
import static com.aitravel.session.config.JwtTestSupport.bearer;
import static com.aitravel.session.config.JwtTestSupport.expiredToken;
import static com.aitravel.session.config.JwtTestSupport.tokenSignedByUnknownKey;
import static com.aitravel.session.config.JwtTestSupport.tokenWithAudience;
import static com.aitravel.session.config.JwtTestSupport.tokenWithIssuer;
import static com.aitravel.session.config.JwtTestSupport.validToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
@TestPropertySource(properties = {ISSUER_PROPERTY, AUDIENCE_PROPERTY})
@Import(JwtTestSupport.TrustedKeyDecoderConfig.class)
class TravelSessionSecurityIntegrationTest {

    private static final String SESSIONS = "/api/v1/travel-sessions";
    private static final String CREATE_BODY = """
            {"interests":["HISTORY"],"maxBudget":50,"currencyCode":"EUR","availableMinutes":180}
            """;
    private static final String LOCATION_BODY = """
            {"location":{"latitude":41.9028,"longitude":12.4964}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post(SESSIONS).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenCanCreateSession() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(ALICE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken(ALICE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidIssuerIsUnauthorized() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(tokenWithIssuer(ALICE, "https://evil.example.com/oauth2/default")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAudienceIsUnauthorized() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenWithAudience(ALICE, "api://other-service")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenSignedByUnknownKey(ALICE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherUsersCannotRefreshOrStopSession() throws Exception {
        String createdBody = mockMvc.perform(post(SESSIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(ALICE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(createdBody, "$.sessionId");

        mockMvc.perform(post(SESSIONS + "/" + sessionId + "/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(BOB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCATION_BODY))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(SESSIONS + "/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(BOB))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(SESSIONS + "/" + sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(validToken(ALICE))))
                .andExpect(status().isNoContent());
    }
}
