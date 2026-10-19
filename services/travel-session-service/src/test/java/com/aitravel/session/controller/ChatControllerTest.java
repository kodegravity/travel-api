package com.aitravel.session.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.session.client.ChatClient;
import com.aitravel.session.config.JwtTestSupport;
import com.aitravel.session.exception.AttractionProviderException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
@Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
class ChatControllerTest {

    private static final String BODY = """
            {"messages": [{"role": "USER", "content": "What should I do now?"}],
             "context": {"location": {"latitude": 40.4169, "longitude": -3.7035}, "placeName": "Madrid",
                         "localDateTime": "2026-10-19T15:30", "interests": ["ART"], "maxBudget": 40,
                         "currencyCode": "EUR", "availableMinutes": 180}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatClient client;

    @Test
    void forwardsTheReply() throws Exception {
        given(client.replyJson(any())).willReturn("{\"reply\":\"Try the Prado.\",\"suggestions\":[],\"aiWritten\":true}");

        mockMvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Try the Prado."));
    }

    @Test
    void rejectsBadRequestsBeforeAnyCall() throws Exception {
        for (String body : List.of(
                BODY.replace("\"USER\"", "\"ASSISTANT\""),
                BODY.replace("2026-10-19T15:30", "now"),
                BODY.replace("\"EUR\"", "\"euros\""),
                BODY.replace("\"What should I do now?\"", "\"" + "x".repeat(1001) + "\""))) {
            mockMvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(client, never()).replyJson(any());
    }

    @Test
    void downstreamFailureIsServiceUnavailable() throws Exception {
        given(client.replyJson(any())).willThrow(new AttractionProviderException("down", null));

        mockMvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
