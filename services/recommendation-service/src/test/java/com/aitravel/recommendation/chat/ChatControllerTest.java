package com.aitravel.recommendation.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.recommendation.config.JwtTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import({JwtTestSupport.TrustedKeyDecoderConfig.class, JwtTestSupport.DefaultJwtMockMvcConfig.class})
class ChatControllerTest {

    private static final String BODY = """
            {"messages": [{"role": "USER", "content": "What should I do now?"}],
             "context": {"location": {"latitude": 40.4169, "longitude": -3.7035}, "placeName": "Madrid",
                         "localDateTime": "2026-10-19T15:30", "interests": ["ART"], "maxBudget": 40,
                         "currencyCode": "EUR", "availableMinutes": 180, "tripStops": ["Royal Palace"]}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService service;

    @Test
    void returnsTheReply() throws Exception {
        given(service.reply(any())).willReturn(new ChatResponse("Try the Prado.", List.of(), null, true));

        mockMvc.perform(post("/internal/v1/chat").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Try the Prado."))
                .andExpect(jsonPath("$.aiWritten").value(true))
                .andExpect(jsonPath("$.weather").doesNotExist());
    }

    @Test
    void rejectsBadRequests() throws Exception {
        for (String body : List.of(
                BODY.replace("\"USER\"", "\"ASSISTANT\""),
                BODY.replace("2026-10-19T15:30", "tomorrow"),
                BODY.replace("\"What should I do now?\"", "\"" + "x".repeat(1001) + "\""),
                BODY.replace("[{\"role\": \"USER\", \"content\": \"What should I do now?\"}]", "[]"))) {
            mockMvc.perform(post("/internal/v1/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).reply(any());
    }
}
