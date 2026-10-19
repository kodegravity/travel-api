package com.aitravel.recommendation.chat;

import com.aitravel.recommendation.model.Attraction;
import com.aitravel.recommendation.model.Interest;
import java.util.Set;

/** A real nearby place the assistant may suggest, with its distance and whether it suits rain. */
record ChatCandidate(Attraction attraction, double distanceKm, Setting setting) {

    enum Setting { INDOOR, OUTDOOR, MIXED }

    static Setting settingOf(Set<Interest> categories) {
        if (categories.contains(Interest.NATURE)) {
            return Setting.OUTDOOR;
        }
        if (categories.contains(Interest.ART) || categories.contains(Interest.SHOPPING) || categories.contains(Interest.FOOD)) {
            return Setting.INDOOR;
        }
        return Setting.MIXED;
    }
}
