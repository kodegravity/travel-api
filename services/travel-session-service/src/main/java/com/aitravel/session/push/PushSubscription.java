package com.aitravel.session.push;

import java.time.Instant;

/**
 * One browser's push subscription, as the browser's PushManager returned it.
 *
 * @param endpoint  the push service URL for this browser
 * @param p256dh    the browser's public key, base64url
 * @param auth      the browser's auth secret, base64url
 * @param createdAt when the traveler turned notifications on in this browser
 */
public record PushSubscription(String endpoint, String p256dh, String auth, Instant createdAt) {
}
