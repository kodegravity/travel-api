package com.aitravel.session.push;

/** Push is turned off, or the subscription could not be stored. Answered with 503. */
public class PushUnavailableException extends RuntimeException {

    public PushUnavailableException(String message) {
        super(message);
    }
}
