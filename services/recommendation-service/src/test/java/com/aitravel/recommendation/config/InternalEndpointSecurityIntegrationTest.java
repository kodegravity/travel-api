package com.aitravel.recommendation.config;

import static com.aitravel.recommendation.config.JwtTestSupport.AUDIENCE_PROPERTY;
import static com.aitravel.recommendation.config.JwtTestSupport.ISSUER_PROPERTY;
import static com.aitravel.recommendation.config.JwtTestSupport.bearer;
import static com.aitravel.recommendation.config.JwtTestSupport.expiredToken;
import static com.aitravel.recommendation.config.JwtTestSupport.tokenSignedByUnknownKey;
import static com.aitravel.recommendation.config.JwtTestSupport.tokenWithAudience;
import static com.aitravel.recommendation.config.JwtTestSupport.tokenWithIssuer;
import static com.aitravel.recommendation.config.JwtTestSupport.validToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.recommendation.provider.AttractionProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {ISSUER_PROPERTY, AUDIENCE_PROPERTY})
@Import(JwtTestSupport.TrustedKeyDecoderConfig.class)
class InternalEndpointSecurityIntegrationTest {

    private static final String PATH = "/internal/v1/recommendations";
    private static final String BODY = """
            {"request": {"location": {"latitude": 41.9028, "longitude": 12.4964},
              "interests": ["HISTORY"], "maxBudget": 50, "currencyCode": "EUR", "availableMinutes": 180},
             "excludedPlaceIds": []}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttractionProvider attractionProvider;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(internal()).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(internal().header(HttpHeaders.AUTHORIZATION, bearer(expiredToken("alice-sub"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerIsUnauthorized() throws Exception {
        mockMvc.perform(internal().header(HttpHeaders.AUTHORIZATION,
                        bearer(tokenWithIssuer("alice-sub", "https://evil.example.com"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceIsUnauthorized() throws Exception {
        mockMvc.perform(internal().header(HttpHeaders.AUTHORIZATION,
                        bearer(tokenWithAudience("alice-sub", "api://other"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void untrustedSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(internal().header(HttpHeaders.AUTHORIZATION,
                        bearer(tokenSignedByUnknownKey("alice-sub"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReachesEndpoint() throws Exception {
        when(attractionProvider.findNearby(any(), any())).thenReturn(List.of());

        mockMvc.perform(internal().header(HttpHeaders.AUTHORIZATION, bearer(validToken("alice-sub"))))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder internal() {
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY);
    }
}
