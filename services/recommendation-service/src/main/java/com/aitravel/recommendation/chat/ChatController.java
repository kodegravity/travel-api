package com.aitravel.recommendation.chat;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Ask ROAM" chat for the Travel Session Service. Messages and locations are never logged. */
@RestController
@RequestMapping("/internal/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        log.info("POST /internal/v1/chat received turns={} location={}", request.messages().size(),
                request.context().location() != null);
        ChatResponse response = chatService.reply(request);
        log.info("POST /internal/v1/chat completed aiWritten={} suggestions={}", response.aiWritten(),
                response.suggestions().size());
        return response;
    }
}
