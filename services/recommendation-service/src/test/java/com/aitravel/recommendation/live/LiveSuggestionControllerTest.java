package com.aitravel.recommendation.live;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.recommendation.config.JwtTestSupport;
import com.aitravel.recommendation.exception.AttractionProviderException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LiveSuggestionController.class)
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
class LiveSuggestionControllerTest {

    private static final String BODY = """
            {"location": {"latitude": 40.4169, "longitude": -3.7035}, "interests": ["ART"],
             "eventTypes": ["SPORTS", "MUSIC"], "excludedEventIds": ["A1"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveSuggestionService service;

    @Test
    void returnsSuggestions() throws Exception {
        given(service.suggest(any())).willReturn(List.of(
                new LiveSuggestion(LiveFixtures.GAME, "Game nearby: Real Madrid vs Sevilla", "Bernabéu, about 4.2 km from you.", false)));

        mockMvc.perform(post("/internal/v1/live-suggestions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].event.id").value("G1"))
                .andExpect(jsonPath("$.suggestions[0].headline").value("Game nearby: Real Madrid vs Sevilla"))
                .andExpect(jsonPath("$.suggestions[0].aiWritten").value(false));
    }

    @Test
    void rejectsBadRequests() throws Exception {
        for (String body : List.of(
                BODY.replace("\"SPORTS\", \"MUSIC\"", ""),
                BODY.replace("40.4169", "140"),
                BODY.replace("\"A1\"", "\"../x\""))) {
            mockMvc.perform(post("/internal/v1/live-suggestions").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).suggest(any());
    }

    @Test
    void eventSourceFailureIsServiceUnavailable() throws Exception {
        given(service.suggest(any())).willThrow(new AttractionProviderException("down", null));

        mockMvc.perform(post("/internal/v1/live-suggestions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
