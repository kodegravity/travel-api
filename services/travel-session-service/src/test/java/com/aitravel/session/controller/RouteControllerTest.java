package com.aitravel.session.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.session.client.RouteClient;
import com.aitravel.session.config.JwtTestSupport;
import com.aitravel.session.dto.RouteOptimizeRequest;
import com.aitravel.session.exception.AttractionProviderException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
class RouteControllerTest {

    private static final String BODY = """
            {"mode": "DRIVE", "origin": {"latitude": 40.4169, "longitude": -3.7035},
             "stops": [{"id": "palace", "latitude": 40.4179, "longitude": -3.7143},
                       {"id": "retiro", "latitude": 40.4153, "longitude": -3.6844}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteClient client;

    @Test
    void returnsTheRouteJson() throws Exception {
        given(client.optimizeJson(any())).willReturn("{\"order\":[\"palace\",\"retiro\"],\"source\":\"google\"}");

        mockMvc.perform(post("/api/v1/routes/optimize").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order[1]").value("retiro"))
                .andExpect(jsonPath("$.source").value("google"));

        ArgumentCaptor<RouteOptimizeRequest> sent = ArgumentCaptor.forClass(RouteOptimizeRequest.class);
        verify(client).optimizeJson(sent.capture());
        assertThat(sent.getValue().mode()).isEqualTo(RouteOptimizeRequest.Mode.DRIVE);
        assertThat(sent.getValue().stops()).extracting(RouteOptimizeRequest.Stop::id).containsExactly("palace", "retiro");
    }

    @Test
    void rejectsBadRequestsBeforeAnyCall() throws Exception {
        String transit = BODY.replace("DRIVE", "TRANSIT");
        String duplicate = BODY.replace("retiro", "palace");
        String badLatitude = BODY.replace("40.4169", "140");
        String noStops = BODY.replaceAll("\"stops\": \\[[\\s\\S]*\\]", "\"stops\": []");

        for (String body : List.of(transit, duplicate, badLatitude, noStops)) {
            mockMvc.perform(post("/api/v1/routes/optimize").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(client, never()).optimizeJson(any());
    }

    @Test
    void downstreamFailureIsServiceUnavailable() throws Exception {
        given(client.optimizeJson(any())).willThrow(new AttractionProviderException("down", null));

        mockMvc.perform(post("/api/v1/routes/optimize").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
