package com.aitravel.places.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.places.config.JwtTestSupport;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.PlaceDetails;
import com.aitravel.places.provider.PlaceDetailsProvider;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaceDetailsController.class)
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
class PlaceDetailsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceDetailsProvider provider;

    @Test
    void returnsDetailsAndOmitsUnknownFields() throws Exception {
        given(provider.findDetails("pantheon")).willReturn(Optional.of(new PlaceDetails("pantheon", "Pantheon", null,
                new BigDecimal("41.8986"), new BigDecimal("12.4769"), 4.8, 100, null, null, null, null,
                "Roman temple.", null, null, null, null, "sample")));

        mockMvc.perform(get("/internal/v1/places/pantheon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pantheon"))
                .andExpect(jsonPath("$.rating").value(4.8))
                .andExpect(jsonPath("$.summary").value("Roman temple."))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.source").value("sample"));
    }

    @Test
    void unknownPlaceIsNotFound() throws Exception {
        given(provider.findDetails("nowhere")).willReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/places/nowhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Place not found"));
    }

    @Test
    void rejectsIdsThatCouldReachAnotherProviderPath() throws Exception {
        mockMvc.perform(get("/internal/v1/places/abc%2E%2Ewhatever!"))
                .andExpect(status().isBadRequest());
        verify(provider, never()).findDetails(anyString());
    }

    @Test
    void providerFailureIsServiceUnavailable() throws Exception {
        given(provider.findDetails("pantheon")).willThrow(new AttractionProviderException("limit", null));

        mockMvc.perform(get("/internal/v1/places/pantheon"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Attraction provider unavailable"));
    }

    @Test
    void returnsACardPhoto() throws Exception {
        given(provider.findPhoto("ChIJ-abc_123")).willReturn(Optional.of(new PlaceDetails.Photo(
                "https://lh3.googleusercontent.com/p/small", 4000, 3000,
                java.util.List.of(new PlaceDetails.Attribution("Luis", "https://www.google.com/maps/contrib/2")))));

        mockMvc.perform(get("/internal/v1/places/ChIJ-abc_123/photo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://lh3.googleusercontent.com/p/small"))
                .andExpect(jsonPath("$.attributions[0].displayName").value("Luis"));
    }

    @Test
    void placeWithoutPhotoIsNotFound() throws Exception {
        given(provider.findPhoto("pantheon")).willReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/places/pantheon/photo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No photo"));
    }
}
