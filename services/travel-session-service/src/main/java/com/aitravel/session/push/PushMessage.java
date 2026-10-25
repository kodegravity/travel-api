package com.aitravel.session.push;

/**
 * What the service worker shows. The same {@code tag} as the page's own notifications, so a push and an in-page
 * notification about the same thing replace each other instead of stacking.
 *
 * @param url path the notification opens, for example {@code /travel-mode}
 */
public record PushMessage(String title, String body, String tag, String url) {
}
