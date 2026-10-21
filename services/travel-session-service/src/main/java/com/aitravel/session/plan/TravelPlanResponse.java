package com.aitravel.session.plan;

import java.util.Map;

/**
 * A traveler's synced plan.
 *
 * @param ownerRef one-way hash of the signed-in traveler, so the web app can tell that a different account signed in
 * @param version  0 when nothing is stored yet; send it back as {@code baseVersion} with the next save
 * @param entries  keyed {@code city:<cityId>} or {@code favorites}
 */
public record TravelPlanResponse(String ownerRef, long version, Map<String, TravelPlanEntry> entries) {
}
