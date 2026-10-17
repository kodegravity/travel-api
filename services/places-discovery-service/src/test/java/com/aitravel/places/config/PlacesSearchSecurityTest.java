package com.aitravel.places.config;

import static com.aitravel.places.config.JwtTestSupport.AUDIENCE_PROPERTY;
import static com.aitravel.places.config.JwtTestSupport.ISSUER_PROPERTY;
import static com.aitravel.places.config.JwtTestSupport.bearer;
import static com.aitravel.places.config.JwtTestSupport.expiredToken;
import static com.aitravel.places.config.JwtTestSupport.tokenSignedByUnknownKey;
import static com.aitravel.places.config.JwtTestSupport.tokenWithAudience;
import static com.aitravel.places.config.JwtTestSupport.tokenWithIssuer;
import static com.aitravel.places.config.JwtTestSupport.validToken;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.places.provider.AttractionProvider;
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
class PlacesSearchSecurityTest {

    private static final String PATH = "/internal/v1/places/search";
    private static final String BODY = """
            {"location":{"latitude":41.9028,"longitude":12.4964},"searchRadiusKm":5.0}""";

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
        mockMvc.perform(search()).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        mockMvc.perform(search().header(HttpHeaders.AUTHORIZATION, bearer(expiredToken("alice-sub"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerIsUnauthorized() throws Exception {
        mockMvc.perform(search().header(HttpHeaders.AUTHORIZATION,
                        bearer(tokenWithIssuer("alice-sub", "https://evil.example.com"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceIsUnauthorized() throws Exception {
        mockMvc.perform(search().header(HttpHeaders.AUTHORIZATION,
                        bearer(tokenWithAudience("alice-sub", "api://other"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void untrustedSignatureIsUnauthorized() throws Exception {
        mockMvc.perform(search().header(HttpHeaders.AUTHORIZATION,
                        bearer(tokenSignedByUnknownKey("alice-sub"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenReachesEndpoint() throws Exception {
        given(attractionProvider.findNearby(any(), any(), anyDouble())).willReturn(List.of());

        mockMvc.perform(search().header(HttpHeaders.AUTHORIZATION, bearer(validToken("alice-sub"))))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder search() {
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY);
    }
}
