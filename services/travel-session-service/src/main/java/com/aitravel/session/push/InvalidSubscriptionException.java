package com.aitravel.session.push;

/** The subscription does not point at a known push service, or its keys are malformed. Answered with 400. */
public class InvalidSubscriptionException extends RuntimeException {

    public InvalidSubscriptionException(String message) {
        super(message);
    }
}
