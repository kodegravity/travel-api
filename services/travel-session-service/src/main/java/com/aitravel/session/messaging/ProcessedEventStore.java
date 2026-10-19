package com.aitravel.session.messaging;

/** Remembers which events a consumer has handled, so SQS redeliveries are processed once. */
public interface ProcessedEventStore {

    /** Records the event as handled by {@code consumer}. False when it already was. */
    boolean markProcessed(String consumer, String eventId);

    /** Forgets a claim whose handling failed, so the SQS retry processes the event again. */
    void release(String consumer, String eventId);
}
