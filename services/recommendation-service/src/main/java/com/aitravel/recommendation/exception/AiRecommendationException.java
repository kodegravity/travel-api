package com.aitravel.recommendation.exception;

/** Raised when the AI model call fails. Messages must not include credentials or raw prompts. */
public class AiRecommendationException extends RuntimeException {

    public AiRecommendationException(String message, Throwable cause) {
        super(message, cause);
    }
}
