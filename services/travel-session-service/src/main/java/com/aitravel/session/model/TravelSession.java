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
 * <p>Holds preferences, the last evaluated location, and recommended place IDs, and nothing else. Mutable state is
 * guarded by this object's monitor. The refresh guard is a separate atomic flag, so a slow provider call never holds
 * the session lock.
 *
 * <p>{@link #version()} supports optimistic locking in shared stores: a store saves a session only if the stored
 * version still matches, then calls {@link #markSaved(long)}. {@link #state()} and {@link #restore(State)} convert
 * to and from the persisted form.
 */
public final class TravelSession {

    private final UUID sessionId;
    private final String ownerSubject;
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
    private long version;

    public TravelSession(UUID sessionId, String ownerSubject, Set<Interest> interests, BigDecimal maxBudget, String currencyCode,
                         Integer availableMinutes, Instant now, Duration inactivityTimeout, Duration maxLifetime) {
        this.sessionId = sessionId;
        this.ownerSubject = ownerSubject;
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

    private TravelSession(State state) {
        this.sessionId = state.sessionId();
        this.ownerSubject = state.ownerSubject();
        this.createdAt = state.createdAt();
        this.inactivityTimeout = state.inactivityTimeout();
        this.hardExpiresAt = state.hardExpiresAt();
        this.interests = Set.copyOf(state.interests());
        this.maxBudget = state.maxBudget();
        this.currencyCode = state.currencyCode();
        this.availableMinutes = state.availableMinutes();
        this.status = state.status();
        this.lastActivityAt = state.lastActivityAt();
        this.expiresAt = state.expiresAt();
        this.lastEvaluatedLocation = state.lastEvaluatedLocation();
        this.lastRecommendationTime = state.lastRecommendationTime();
        this.recommendedPlaceIds.addAll(state.recommendedPlaceIds());
        this.version = state.version();
    }

    /** Rebuilds a session read from a store. The refresh guard starts released; stores manage their own lease. */
    public static TravelSession restore(State state) {
        return new TravelSession(state);
    }

    /** Full persisted form of this session, including its version. */
    public synchronized State state() {
        return new State(sessionId, ownerSubject, createdAt, inactivityTimeout, hardExpiresAt, interests, maxBudget,
                currencyCode, availableMinutes, status, lastActivityAt, expiresAt, lastEvaluatedLocation,
                lastRecommendationTime, Set.copyOf(recommendedPlaceIds), version);
    }

    /** Version this copy was read at. 0 means it has never been stored. */
    public synchronized long version() {
        return version;
    }

    /** Records that a store saved this copy as {@code newVersion}. */
    public synchronized void markSaved(long newVersion) {
        this.version = newVersion;
    }

    public UUID sessionId() {
        return sessionId;
    }

    /** The Okta {@code sub} of the user who created this session. */
    public String ownerSubject() {
        return ownerSubject;
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

    /** Everything a store needs to persist and rebuild a session. */
    public record State(
            UUID sessionId,
            String ownerSubject,
            Instant createdAt,
            Duration inactivityTimeout,
            Instant hardExpiresAt,
            Set<Interest> interests,
            BigDecimal maxBudget,
            String currencyCode,
            Integer availableMinutes,
            TravelSessionStatus status,
            Instant lastActivityAt,
            Instant expiresAt,
            Location lastEvaluatedLocation,
            Instant lastRecommendationTime,
            Set<String> recommendedPlaceIds,
            long version
    ) {
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
