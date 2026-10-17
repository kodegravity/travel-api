package com.aitravel.session.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary Active Travel Mode session.
 *
 * <p>Holds preferences, the last evaluated location, and recommended place IDs, and nothing else. Restarting
 * the application discards every session. Mutable state is guarded by this object's monitor. The refresh guard
 * is a separate atomic flag, so a slow provider call never holds the session lock.
 */
public final class TravelSession {

    private final UUID sessionId;
    private final Instant createdAt;
    private final Duration inactivityTimeout;
    private final Instant hardExpiresAt;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    // Preferences are cleared when the session stops or expires.
    private Set<Interest> interests;
    private BigDecimal maxBudget;
    private String currencyCode;
    private Integer availableMinutes;

    private TravelSessionStatus status = TravelSessionStatus.ACTIVE;
    private Instant lastActivityAt;
    private Instant expiresAt;
    private Location lastEvaluatedLocation;
    private Instant lastRecommendationTime;
    private final Set<String> recommendedPlaceIds = new HashSet<>();

    public TravelSession(UUID sessionId, Set<Interest> interests, BigDecimal maxBudget, String currencyCode,
                         Integer availableMinutes, Instant now, Duration inactivityTimeout, Duration maxLifetime) {
        this.sessionId = sessionId;
        this.interests = Set.copyOf(interests);
        this.maxBudget = maxBudget;
        this.currencyCode = currencyCode;
        this.availableMinutes = availableMinutes;
        this.createdAt = now;
        this.lastActivityAt = now;
        this.inactivityTimeout = inactivityTimeout;
        this.hardExpiresAt = now.plus(maxLifetime);
        this.expiresAt = expiryAfter(now);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized TravelSessionStatus status() {
        return status;
    }

    public synchronized Instant lastActivityAt() {
        return lastActivityAt;
    }

    public synchronized Instant expiresAt() {
        return expiresAt;
    }

    public synchronized boolean isActiveAt(Instant now) {
        return status == TravelSessionStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /** True once the session is past its expiry plus the retention window, so its tombstone can be removed. */
    public synchronized boolean isPurgeableAt(Instant now, Duration retentionAfterExpiry) {
        return !now.isBefore(expiresAt.plus(retentionAfterExpiry));
    }

    /** Moves an active session to EXPIRED once its expiry time has passed, and wipes its temporary data. */
    public synchronized void expireIfDue(Instant now) {
        if (status == TravelSessionStatus.ACTIVE && !now.isBefore(expiresAt)) {
            status = TravelSessionStatus.EXPIRED;
            wipe();
        }
    }

    /** Extends the inactivity expiry for a valid interaction, capped at the absolute lifetime. */
    public synchronized void touch(Instant now) {
        if (status == TravelSessionStatus.ACTIVE && now.isBefore(expiresAt)) {
            lastActivityAt = now;
            expiresAt = expiryAfter(now);
        }
    }

    /**
     * Records a successful evaluation: moves the movement baseline and cooldown clock, and adds the place IDs to
     * the exclusion set. Returns false, changing nothing, if the session is no longer active.
     */
    public synchronized boolean recordEvaluation(Location location, Instant now, Collection<String> placeIds) {
        if (status != TravelSessionStatus.ACTIVE || !now.isBefore(expiresAt)) {
            return false;
        }
        recommendedPlaceIds.addAll(placeIds);
        lastEvaluatedLocation = location;
        lastRecommendationTime = now;
        lastActivityAt = now;
        expiresAt = expiryAfter(now);
        return true;
    }

    /** Stops the session and clears its preferences, location, and recommendation history. Idempotent. */
    public synchronized void stop() {
        status = TravelSessionStatus.STOPPED;
        wipe();
    }

    /** Copies the state needed for one refresh so the lock can be released before any external call. */
    public synchronized Snapshot snapshot() {
        return new Snapshot(interests, maxBudget, currencyCode, availableMinutes,
                lastEvaluatedLocation, lastRecommendationTime, Set.copyOf(recommendedPlaceIds));
    }

    /** Returns true if this caller may run a refresh. A second concurrent caller gets false. */
    public boolean tryBeginRefresh() {
        return refreshInProgress.compareAndSet(false, true);
    }

    public void endRefresh() {
        refreshInProgress.set(false);
    }

    private Instant expiryAfter(Instant now) {
        Instant idleExpiry = now.plus(inactivityTimeout);
        return idleExpiry.isBefore(hardExpiresAt) ? idleExpiry : hardExpiresAt;
    }

    private void wipe() {
        interests = Set.of();
        maxBudget = null;
        currencyCode = null;
        availableMinutes = null;
        lastEvaluatedLocation = null;
        lastRecommendationTime = null;
        recommendedPlaceIds.clear();
    }

    /** Immutable view of a session's state at one point in time. */
    public record Snapshot(
            Set<Interest> interests,
            BigDecimal maxBudget,
            String currencyCode,
            Integer availableMinutes,
            Location lastEvaluatedLocation,
            Instant lastRecommendationTime,
            Set<String> recommendedPlaceIds
    ) {
    }
}
