package com.aitravel.session.plan;

import org.springframework.http.HttpStatus;

/** A plan save that cannot be applied. Each reason has a fixed status and message. */
public class TravelPlanException extends RuntimeException {

    public enum Reason {
        VERSION_CONFLICT(HttpStatus.CONFLICT, "Travel plan changed",
                "The travel plan was saved from another device. Load it again, merge, and retry."),
        TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Travel plan too large",
                "The travel plan is too large to save. Remove some trips or itineraries and retry.");

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

    public TravelPlanException(Reason reason) {
        super(reason.title());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
