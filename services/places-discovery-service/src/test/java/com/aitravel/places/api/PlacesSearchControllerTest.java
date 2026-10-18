package com.aitravel.places.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.Attraction;
import com.aitravel.places.model.Interest;
import com.aitravel.places.provider.AttractionProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import com.aitravel.places.config.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlacesSearchController.class)
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
class PlacesSearchControllerTest {

    private static final String PATH = "/internal/v1/places/search";
    private static final String VALID_BODY = """
            {"location":{"latitude":41.9028,"longitude":12.4964},"searchRadiusKm":5.0}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttractionProvider attractionProvider;

    @Test
    void returnsNormalizedAttractionsAndPassesCoordinatesAndRadiusToProvider() throws Exception {
        given(attractionProvider.findNearby(any(), any(), anyDouble())).willReturn(List.of(new Attraction(
                "colosseum", "Colosseum", Set.of(Interest.HISTORY),
                new BigDecimal("41.8902"), new BigDecimal("12.4922"),
                null, null, 90, null)));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attractions[0].id").value("colosseum"))
                .andExpect(jsonPath("$.attractions[0].name").value("Colosseum"))
                .andExpect(jsonPath("$.attractions[0].categories[0]").value("HISTORY"))
                .andExpect(jsonPath("$.attractions[0].latitude").value(41.8902))
                .andExpect(jsonPath("$.attractions[0].estimatedDurationMinutes").value(90))
                .andExpect(jsonPath("$.attractions[0].estimatedCost").doesNotExist());

        then(attractionProvider).should().findNearby(new BigDecimal("41.9028"), new BigDecimal("12.4964"), 5.0);
    }

    @Test
    void unexpectedProviderErrorReturnsGenericProblemDetail() throws Exception {
        given(attractionProvider.findNearby(any(), any(), anyDouble()))
                .willThrow(new IllegalStateException("internal detail lat=41.9028"));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."));
    }

    @Test
    void returnsOkWithEmptyListWhenNoAttractionsExist() throws Exception {
        given(attractionProvider.findNearby(any(), any(), anyDouble())).willReturn(List.of());

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attractions").isEmpty());
    }

    @Test
    void rejectsLatitudeOutOfRangeWithFieldError() throws Exception {
        String body = """
                {"location":{"latitude":91,"longitude":12.4964},"searchRadiusKm":5.0}""";

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['location.latitude']").exists());
    }

    @Test
    void rejectsLongitudeOutOfRangeWithFieldError() throws Exception {
        String body = """
                {"location":{"latitude":41.9,"longitude":-181},"searchRadiusKm":5.0}""";

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['location.longitude']").exists());
    }

    @Test
    void rejectsMissingLocation() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"searchRadiusKm\":5.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['location']").exists());
    }

    @Test
    void rejectsRadiusAboveGoogleCircleLimit() throws Exception {
        String body = """
                {"location":{"latitude":41.9,"longitude":12.5},"searchRadiusKm":50.5}""";

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['searchRadiusKm']").exists());
    }

    @Test
    void rejectsMissingRadius() throws Exception {
        String body = """
                {"location":{"latitude":41.9,"longitude":12.5}}""";

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['searchRadiusKm']").exists());
    }

    @Test
    void rejectsMalformedJsonWithFixedMessage() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void returnsServiceUnavailableWithGenericMessageWhenProviderFails() throws Exception {
        given(attractionProvider.findNearby(any(), any(), anyDouble()))
                .willThrow(new AttractionProviderException("Google Places request failed with HTTP 403", null));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Attraction data is temporarily unavailable. Please try again later."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("403"))));
    }
}
