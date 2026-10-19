package com.aitravel.places.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Response shape of Ticketmaster Discovery API {@code GET /discovery/v2/events.json}; only fields we use. */
final class TicketmasterApi {

    private TicketmasterApi() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(@JsonProperty("_embedded") Embedded embedded) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Embedded(List<Event> events) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Event(String id, String name, String url, Dates dates, List<Classification> classifications,
                 List<Image> images, Double distance, @JsonProperty("_embedded") EventEmbedded embedded) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Dates(Start start, Status status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Start(String localDate, String localTime, String dateTime, Boolean dateTBA, Boolean timeTBA) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Status(String code) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Classification(Boolean primary, Named segment, Named genre) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Named(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Image(String url, String ratio, Integer width) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventEmbedded(List<Venue> venues) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Venue(String name, Location location) {
    }

    /** Ticketmaster sends coordinates as strings. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Location(String latitude, String longitude) {
    }
}
