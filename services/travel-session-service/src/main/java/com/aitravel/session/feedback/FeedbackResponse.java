package com.aitravel.session.feedback;

import java.util.List;

/** The traveler's rated places, newest first. */
public record FeedbackResponse(List<PlaceFeedback> places) {
}
