package com.aitravel.places.api;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.places.config.JwtTestSupport;
import com.aitravel.places.exception.AttractionProviderException;
import com.aitravel.places.model.LiveEvent;
import com.aitravel.places.model.LiveEvent.EventType;
import com.aitravel.places.provider.EventProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventsController.class)
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
class EventsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventProvider provider;

    @Test
    void returnsNearbyEvents() throws Exception {
        given(provider.findNearby(eq(40.4169), eq(-3.7035), eq(5.0), any(), any())).willReturn(List.of(
                new LiveEvent("G1", "Real Madrid vs Sevilla", EventType.SPORTS, "Soccer", "Bernabéu", 40.45, -3.69,
                        Instant.parse("2026-11-14T20:00:00Z"), "2026-11-14", "21:00:00", 4.1,
                        "https://www.ticketmaster.es/event/G1", null, "ticketmaster")));

        mockMvc.perform(get("/internal/v1/events/nearby").param("latitude", "40.4169").param("longitude", "-3.7035"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Real Madrid vs Sevilla"))
                .andExpect(jsonPath("$[0].type").value("SPORTS"))
                .andExpect(jsonPath("$[0].startsAt").value("2026-11-14T20:00:00Z"))
                .andExpect(jsonPath("$[0].imageUrl").doesNotExist());
    }

    @Test
    void rejectsBadCoordinatesBeforeAnyCall() throws Exception {
        mockMvc.perform(get("/internal/v1/events/nearby").param("latitude", "120").param("longitude", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/internal/v1/events/nearby").param("latitude", "40").param("longitude", "0").param("hours", "99"))
                .andExpect(status().isBadRequest());
        verify(provider, never()).findNearby(anyDouble(), anyDouble(), anyDouble(), any(), any());
    }

    @Test
    void providerFailureIsServiceUnavailable() throws Exception {
        given(provider.findNearby(anyDouble(), anyDouble(), anyDouble(), any(), any()))
                .willThrow(new AttractionProviderException("Ticketmaster failed with HTTP 500", null));

        mockMvc.perform(get("/internal/v1/events/nearby").param("latitude", "40").param("longitude", "-3"))
                .andExpect(status().isServiceUnavailable());
    }
}
