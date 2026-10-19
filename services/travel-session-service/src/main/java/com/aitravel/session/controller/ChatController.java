package com.aitravel.session.controller;

import com.aitravel.session.client.ChatClient;
import com.aitravel.session.dto.ChatRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Ask ROAM": what to do next, from the traveler's location, time, preferences, budget, trip, and the weather.
 * Each reply can cost one OpenAI call (capped per day) and, once per area every 15 minutes, a Google search.
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient client;

    public ChatController(ChatClient client) {
        this.client = client;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@Valid @RequestBody ChatRequest request) {
        log.info("POST /api/v1/chat received turns={}", request.messages().size());
        String body = client.replyJson(request);
        log.info("POST /api/v1/chat completed");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
