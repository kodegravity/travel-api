package com.aitravel.places.api;

import com.aitravel.places.model.LiveEvent;
import com.aitravel.places.provider.EventProvider;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ticketed events near a point, starting from now within the next few hours. Coordinates are never logged. */
@RestController
@RequestMapping("/internal/v1/events")
public class EventsController {

    private static final Logger log = LoggerFactory.getLogger(EventsController.class);

    private final EventProvider eventProvider;
    private final Clock clock;

    @Autowired
    public EventsController(EventProvider eventProvider) {
        this(eventProvider, Clock.systemUTC());
    }

    EventsController(EventProvider eventProvider, Clock clock) {
        this.eventProvider = eventProvider;
        this.clock = clock;
    }

    @GetMapping("/nearby")
    public List<LiveEvent> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
            @RequestParam(defaultValue = "5") @DecimalMin("0.5") @DecimalMax("25") double radiusKm,
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int hours) {
        log.info("GET /internal/v1/events/nearby received radiusKm={} hours={}", radiusKm, hours);
        Instant now = clock.instant();
        List<LiveEvent> events = eventProvider.findNearby(latitude, longitude, radiusKm, now, now.plus(Duration.ofHours(hours)));
        log.info("GET /internal/v1/events/nearby completed count={}", events.size());
        return events;
    }
}
