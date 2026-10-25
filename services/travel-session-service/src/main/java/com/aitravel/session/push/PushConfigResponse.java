package com.aitravel.session.push;

/**
 * @param enabled       whether the server sends pushes at all
 * @param publicKey     VAPID public key to subscribe with; null while disabled
 * @param subscriptions how many browsers this traveler has subscribed
 */
public record PushConfigResponse(boolean enabled, String publicKey, int subscriptions) {
}
