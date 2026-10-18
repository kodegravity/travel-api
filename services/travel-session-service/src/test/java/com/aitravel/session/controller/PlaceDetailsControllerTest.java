package com.aitravel.session.controller;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.session.client.PlaceDetailsClient;
import com.aitravel.session.config.JwtTestSupport;
import com.aitravel.session.exception.AttractionProviderException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
class PlaceDetailsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceDetailsClient client;

    @Test
    void returnsTheDetailsJson() throws Exception {
        given(client.findDetailsJson("ChIJ-abc_123")).willReturn(Optional.of("{\"name\":\"Royal Palace\",\"rating\":4.7}"));

        mockMvc.perform(get("/api/v1/places/ChIJ-abc_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Royal Palace"))
                .andExpect(jsonPath("$.rating").value(4.7));
    }

    @Test
    void unknownPlaceIsNotFound() throws Exception {
        given(client.findDetailsJson("nowhere")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/places/nowhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Place not found"));
    }

    @Test
    void invalidIdIsRejectedBeforeAnyCall() throws Exception {
        mockMvc.perform(get("/api/v1/places/bad!id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.placeId").value("is not a valid place id"));
        verify(client, never()).findDetailsJson(anyString());
    }

    @Test
    void downstreamFailureIsServiceUnavailable() throws Exception {
        given(client.findDetailsJson("pantheon")).willThrow(new AttractionProviderException("down", null));

        mockMvc.perform(get("/api/v1/places/pantheon")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void browsersMayCallItWithABearerToken() throws Exception {
        mockMvc.perform(options("/api/v1/places/pantheon")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsStringIgnoringCase("GET")));
    }

    @Test
    void returnsACardPhotoAndNotFoundWithoutOne() throws Exception {
        given(client.findPhotoJson("ChIJ-abc_123")).willReturn(Optional.of("{\"url\":\"https://lh3.googleusercontent.com/p/x\"}"));
        given(client.findPhotoJson("nophoto")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/places/ChIJ-abc_123/photo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://lh3.googleusercontent.com/p/x"));
        mockMvc.perform(get("/api/v1/places/nophoto/photo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No photo"));
    }
}
