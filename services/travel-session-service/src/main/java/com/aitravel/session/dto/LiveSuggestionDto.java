package com.aitravel.session.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A live event alert.
 *
 * @param aiWritten true when the AI chose the event and wrote the text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveSuggestionDto(LiveEventDto event, String headline, String message, boolean aiWritten) {
}
