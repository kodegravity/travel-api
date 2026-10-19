package com.aitravel.recommendation.live;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One event worth telling the traveler about, with the alert text.
 *
 * @param headline  short notification title
 * @param message   one or two sentences; never states a time or a price (the app shows the event's real start time)
 * @param aiWritten true when the AI chose the event and wrote the text
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveSuggestion(LiveEvent event, String headline, String message, boolean aiWritten) {
}
