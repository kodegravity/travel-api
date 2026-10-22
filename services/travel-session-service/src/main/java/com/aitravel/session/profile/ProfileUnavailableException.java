package com.aitravel.session.profile;

/** The profile database cannot be reached right now. Answered with 503; the rest of the app keeps working. */
public class ProfileUnavailableException extends RuntimeException {

    public ProfileUnavailableException(Throwable cause) {
        super("Profile store unavailable", cause);
    }
}
