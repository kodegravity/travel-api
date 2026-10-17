package com.aitravel.session.exception;

import org.springframework.http.HttpStatus;

/**
 * Failure in Active Travel Mode. Each reason has a fixed status and message, so no session identifiers,
 * locations, or internal state reach the client.
 */
public class TravelSessionException extends RuntimeException {

    public enum Reason {
        NOT_FOUND(HttpStatus.NOT_FOUND, "Travel session not found",
                "No active travel session matches the supplied identifier."),
        EXPIRED(HttpStatus.GONE, "Travel session expired",
                "This travel session has expired. Create a new session to continue."),
        STOPPED(HttpStatus.CONFLICT, "Travel session stopped",
                "This travel session has been stopped and can no longer be refreshed."),
        REFRESH_IN_PROGRESS(HttpStatus.CONFLICT, "Refresh already in progress",
                "A recommendation refresh is already running for this session. Retry after it completes."),
        CAPACITY_REACHED(HttpStatus.SERVICE_UNAVAILABLE, "Travel session capacity reached",
                "The service is at its active session limit. Please try again later.");

        private final HttpStatus status;
        private final String title;
        private final String detail;

        Reason(HttpStatus status, String title, String detail) {
            this.status = status;
            this.title = title;
            this.detail = detail;
        }

        public HttpStatus status() {
            return status;
        }

        public String title() {
            return title;
        }

        public String detail() {
            return detail;
        }
    }

    private final Reason reason;

    public TravelSessionException(Reason reason) {
        super(reason.detail());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
