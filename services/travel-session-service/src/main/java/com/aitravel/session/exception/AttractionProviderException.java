package com.aitravel.session.exception;

/**
 * Raised when an attraction provider cannot return data. Messages never contain credentials or raw
 * provider responses, so they are safe to return to API consumers.
 */
public class AttractionProviderException extends RuntimeException {

    public AttractionProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
