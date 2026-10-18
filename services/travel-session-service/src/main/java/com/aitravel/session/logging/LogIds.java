package com.aitravel.session.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Log-safe identifiers. The Okta {@code sub} can be an email address, so logs carry a short one-way hash instead.
 * The same subject always maps to the same value, so one user's requests can still be correlated.
 */
public final class LogIds {

    private static final int HASH_HEX_CHARS = 12;

    private LogIds() {
    }

    public static String subject(String subject) {
        if (subject == null) {
            return "none";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_HEX_CHARS);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
