package com.aitravel.session.push;

/** Sends a notification to every browser a traveler turned notifications on in. Never throws. */
public interface PushNotifier {

    /** @return how many browsers accepted the message */
    int notify(String subject, PushMessage message);

    static PushNotifier none() {
        return (subject, message) -> 0;
    }
}
