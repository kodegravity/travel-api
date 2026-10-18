package com.aitravel.recommendation.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.recommendation.config.JwtTestSupport;
import com.aitravel.recommendation.exception.AttractionProviderException;
import com.aitravel.recommendation.provider.PlaceDetailsClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalPlaceDetailsController.class)
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
class InternalPlaceDetailsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceDetailsClient client;

    @Test
    void returnsTheDetailsJson() throws Exception {
        given(client.findDetailsJson("pantheon")).willReturn(Optional.of("{\"name\":\"Pantheon\"}"));

        mockMvc.perform(get("/internal/v1/places/pantheon"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Pantheon"));
    }

    @Test
    void unknownPlaceIsNotFound() throws Exception {
        given(client.findDetailsJson("nowhere")).willReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/places/nowhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Place not found"));
    }

    @Test
    void invalidIdIsRejectedBeforeAnyCall() throws Exception {
        mockMvc.perform(get("/internal/v1/places/bad!id")).andExpect(status().isBadRequest());
        verify(client, never()).findDetailsJson(anyString());
    }

    @Test
    void downstreamFailureIsServiceUnavailable() throws Exception {
        given(client.findDetailsJson("pantheon")).willThrow(new AttractionProviderException("down", null));

        mockMvc.perform(get("/internal/v1/places/pantheon")).andExpect(status().isServiceUnavailable());
    }
}
