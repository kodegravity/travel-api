package com.aitravel.session.profile;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Process-local profile store, the default ({@code travel.postgres.enabled=false}). Lost on restart. */
@Repository
@ConditionalOnProperty(prefix = "travel.postgres", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryProfileStore implements ProfileStore {

    private final Map<String, TravelerProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public TravelerProfile touch(String ownerKey, Instant now) {
        return profiles.computeIfAbsent(ownerKey, key -> TravelerProfile.newTraveler(now));
    }

    @Override
    public TravelerProfile save(String ownerKey, ProfileFields fields, Instant now) {
        return profiles.compute(ownerKey, (key, current) -> new TravelerProfile(fields.displayName(),
                fields.homeCity(), fields.interests(), fields.currency(), fields.travelMode(),
                current == null ? now : current.createdAt(), now));
    }
}
