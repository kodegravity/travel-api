package com.aitravel.recommendation.service;

import com.aitravel.recommendation.model.Attraction;

/**
 * An attraction that passed eligibility checks, with its travel metrics from the traveler's location.
 *
 * @param attraction     the attraction
 * @param distanceKm     straight-line distance from the traveler (Haversine)
 * @param walkingMinutes estimated walking time to the attraction
 */
public record CandidateAttraction(Attraction attraction, double distanceKm, double walkingMinutes) {
}
