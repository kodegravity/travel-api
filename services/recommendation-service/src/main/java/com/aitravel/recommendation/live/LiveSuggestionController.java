package com.aitravel.recommendation.live;

import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Live event alerts for the Travel Session Service. The location is never logged. */
@RestController
@RequestMapping("/internal/v1/live-suggestions")
public class LiveSuggestionController {

    private static final Logger log = LoggerFactory.getLogger(LiveSuggestionController.class);

    private final LiveSuggestionService service;

    public LiveSuggestionController(LiveSuggestionService service) {
        this.service = service;
    }

    @PostMapping
    public LiveSuggestionResponse suggest(@Valid @RequestBody LiveSuggestionRequest request) {
        log.info("POST /internal/v1/live-suggestions received types={} excluded={}", request.eventTypes().size(),
                request.excludedOrEmpty().size());
        List<LiveSuggestion> suggestions = service.suggest(request);
        log.info("POST /internal/v1/live-suggestions completed count={}", suggestions.size());
        return new LiveSuggestionResponse(suggestions);
    }

    public record LiveSuggestionResponse(List<LiveSuggestion> suggestions) {
    }
}
