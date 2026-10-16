package com.aitravel.companion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class DistanceCalculatorTest {

    @Test
    void samePointIsZeroKilometres() {
        assertThat(DistanceCalculator.haversineKm(41.8902, 12.4922, 41.8902, 12.4922)).isZero();
    }

    @Test
    void londonToParisIsAboutThreeHundredFortyThreeKilometres() {
        double distance = DistanceCalculator.haversineKm(51.5074, -0.1278, 48.8566, 2.3522);

        assertThat(distance).isCloseTo(343.6, within(0.5));
    }

    @Test
    void distanceIsSymmetric() {
        double forward = DistanceCalculator.haversineKm(41.8902, 12.4922, 41.9060, 12.4828);
        double backward = DistanceCalculator.haversineKm(41.9060, 12.4828, 41.8902, 12.4922);

        assertThat(forward).isEqualTo(backward, within(1e-9));
    }

    @Test
    void oneDegreeOfLatitudeIsAboutOneHundredElevenKilometres() {
        double distance = DistanceCalculator.haversineKm(41.0, 12.0, 42.0, 12.0);

        assertThat(distance).isCloseTo(111.2, within(0.2));
    }
}
