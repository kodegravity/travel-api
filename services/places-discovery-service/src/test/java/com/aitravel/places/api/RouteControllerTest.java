package com.aitravel.places.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.places.config.JwtTestSupport;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.OptimizedRoute;
import com.aitravel.places.provider.RouteProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RouteController.class)
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
class RouteControllerTest {

    private static final String BODY = """
            {"mode": "WALK", "origin": {"latitude": 40.4169, "longitude": -3.7035},
             "stops": [{"id": "palace", "latitude": 40.4179, "longitude": -3.7143},
                       {"id": "retiro", "latitude": 40.4153, "longitude": -3.6844}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteProvider provider;

    @Test
    void returnsTheOptimizedRoute() throws Exception {
        given(provider.optimize(any())).willReturn(OptimizedRoute.of(List.of("palace", "retiro"),
                List.of(new OptimizedRoute.Leg(900, 700, "abc"), new OptimizedRoute.Leg(2600, 2100, null)), "google"));

        mockMvc.perform(post("/internal/v1/routes/optimize").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order[1]").value("retiro"))
                .andExpect(jsonPath("$.legs[0].polyline").value("abc"))
                .andExpect(jsonPath("$.legs[1].polyline").doesNotExist())
                .andExpect(jsonPath("$.distanceMeters").value(3500))
                .andExpect(jsonPath("$.durationSeconds").value(2800))
                .andExpect(jsonPath("$.source").value("google"));
    }

    @Test
    void rejectsBadRequestsBeforeAnyPaidCall() throws Exception {
        String transit = BODY.replace("WALK", "TRANSIT");
        String oneStop = """
                {"mode": "WALK", "origin": {"latitude": 40.4, "longitude": -3.7},
                 "stops": [{"id": "palace", "latitude": 40.4179, "longitude": -3.7143}]}
                """;
        String duplicate = BODY.replace("retiro", "palace");
        String badId = BODY.replace("retiro", "../etc");

        for (String body : List.of(transit, oneStop, duplicate, badId)) {
            mockMvc.perform(post("/internal/v1/routes/optimize").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(provider, never()).optimize(any());
    }

    @Test
    void providerFailuresAreServiceUnavailable() throws Exception {
        given(provider.optimize(any())).willThrow(new AttractionProviderException("Google Routes daily call limit of 20 reached", null));

        mockMvc.perform(post("/internal/v1/routes/optimize").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
