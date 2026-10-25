package com.aitravel.session.feedback;

/** Feedback could not be saved, because other saves kept getting there first. Answered with 503. */
public class FeedbackUnavailableException extends RuntimeException {

    public FeedbackUnavailableException() {
        super("Feedback could not be saved");
    }
}
