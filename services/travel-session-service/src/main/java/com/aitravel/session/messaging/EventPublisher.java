package com.aitravel.session.messaging;

/** Sends one serialized event to the travel events topic. Throws when it could not be sent. */
public interface EventPublisher {

    void publish(OutboxEntry entry);
}
