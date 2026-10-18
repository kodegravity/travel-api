package com.aitravel.recommendation.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.recommendation.config.JwtTestSupport;
import com.aitravel.recommendation.exception.AttractionProviderException;
import com.aitravel.recommendation.provider.RouteClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalRouteController.class)
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
class InternalRouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteClient client;

    @Test
    void returnsTheRouteJson() throws Exception {
        given(client.optimizeJson("{\"mode\":\"WALK\"}")).willReturn("{\"order\":[\"a\"],\"source\":\"google\"}");

        mockMvc.perform(post("/internal/v1/routes/optimize").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"WALK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order[0]").value("a"));
    }

    @Test
    void downstreamFailureIsServiceUnavailable() throws Exception {
        given(client.optimizeJson(anyString())).willThrow(new AttractionProviderException("down", null));

        mockMvc.perform(post("/internal/v1/routes/optimize").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());
    }
}
