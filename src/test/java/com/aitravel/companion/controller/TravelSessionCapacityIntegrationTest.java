package com.aitravel.companion.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "travel.session.max-active-sessions=1")
@AutoConfigureMockMvc
class TravelSessionCapacityIntegrationTest {

    private static final String VALID_CREATE = """
            {"interests": ["HISTORY"], "maxBudget": 50, "currencyCode": "EUR", "availableMinutes": 120}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void creatingBeyondActiveCapacityReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(post("/api/v1/travel-sessions").contentType("application/json").content(VALID_CREATE))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/travel-sessions").contentType("application/json").content(VALID_CREATE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Travel session capacity reached"));
    }
}
