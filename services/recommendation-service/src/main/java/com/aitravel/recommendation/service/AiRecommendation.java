package com.aitravel.recommendation.service;

/**
 * One AI selection. {@code attractionId} always refers to a candidate that was supplied to the model.
 *
 * @param attractionId the ID of the selected candidate attraction
 * @param reason       a short personalised explanation, grounded in the candidate's known data
 */
public record AiRecommendation(String attractionId, String reason) {
}
