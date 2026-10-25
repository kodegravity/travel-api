package com.aitravel.session.service;

import com.aitravel.session.config.TravelSessionProperties;
import com.aitravel.session.dto.CreateTravelSessionRequest;
import com.aitravel.session.dto.CreateTravelSessionResponse;
import com.aitravel.session.dto.LiveEventDto;
import com.aitravel.session.dto.LiveSuggestionDto;
import com.aitravel.session.dto.LiveSuggestionRequest;
import com.aitravel.session.dto.LocationRequest;
import com.aitravel.session.dto.RecommendationDto;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.dto.RefreshStatus;
import com.aitravel.session.dto.TravelSessionRecommendationResponse;
import com.aitravel.session.exception.AttractionProviderException;
import com.aitravel.session.exception.TravelSessionException.Reason;
import com.aitravel.session.exception.TravelSessionException;
import com.aitravel.session.feedback.PlaceExclusions;
import com.aitravel.session.logging.LogIds;
import com.aitravel.session.messaging.EventOutbox;
import com.aitravel.session.messaging.EventType;
import com.aitravel.session.messaging.OutboxEntry;
import com.aitravel.session.messaging.events.NearbyEventDiscovered;
import com.aitravel.session.messaging.events.RecommendationsReady;
import com.aitravel.session.messaging.events.TravelSessionEnded;
import com.aitravel.session.messaging.events.TravelSessionStarted;
import com.aitravel.session.model.Interest;
import com.aitravel.session.model.Location;
import com.aitravel.session.model.TravelSession;
import com.aitravel.session.model.TravelSessionStatus;
import com.aitravel.session.repository.TravelSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Orchestrates Active Travel Mode. Session state is updated under short per-session locks, and provider calls
 * run without holding any lock. A failed provider call leaves the session state unchanged.
 */
@Service
public class TravelSessionService {

    private static final Logger log = LoggerFactory.getLogger(TravelSessionService.class);
    private static final int MAX_UPDATE_ATTEMPTS = 3;

    private final TravelSessionRepository repository;
    private final RecommendationService recommendationService;
    private final TravelSessionProperties properties;
    private final Clock clock;
    /** Events for SNS, written atomically with the session change that causes them. Does nothing while disabled. */
    private final EventOutbox outbox;
    /** Places the traveler disliked, left out of every refresh like the places already suggested. */
    private final PlaceExclusions exclusions;

    public TravelSessionService(TravelSessionRepository repository,
                                RecommendationService recommendationService,
                                TravelSessionProperties properties,
                                Clock clock) {
        this(repository, recommendationService, properties, clock, EventOutbox.disabled(), PlaceExclusions.none());
    }

    public TravelSessionService(TravelSessionRepository repository,
                                RecommendationService recommendationService,
                                TravelSessionProperties properties,
                                Clock clock,
                                EventOutbox outbox) {
        this(repository, recommendationService, properties, clock, outbox, PlaceExclusions.none());
    }

    @Autowired
    public TravelSessionService(TravelSessionRepository repository,
                                RecommendationService recommendationService,
                                TravelSessionProperties properties,
                                Clock clock,
                                EventOutbox outbox,
                                PlaceExclusions exclusions) {
        this.repository = repository;
        this.recommendationService = recommendationService;
        this.properties = properties;
        this.clock = clock;
        this.outbox = outbox;
        this.exclusions = exclusions;
    }

    public CreateTravelSessionResponse create(CreateTravelSessionRequest request, String ownerSubject) {
        Instant now = clock.instant();
        TravelSession session = new TravelSession(
                UUID.randomUUID(),
                ownerSubject,
                request.interests(),
                request.maxBudget(),
                request.currencyCode(),
                request.availableMinutes(),
                now,
                inactivityTimeout(),
                maxLifetime());

        log.debug("Creating session sessionId={} owner={} maxBudget={} currency={} expiresAt={}",
                session.sessionId(), LogIds.subject(ownerSubject), request.maxBudget(), request.currencyCode(), session.expiresAt());
        List<OutboxEntry> events = outbox.prepare(EventType.TRAVEL_SESSION_STARTED, new TravelSessionStarted(
                session.sessionId().toString(), LogIds.subject(ownerSubject), request.interests(), request.currencyCode(),
                request.availableMinutes()));
        if (!repository.addIfCapacity(session, properties.maxActiveSessions(), now, retention(), events)) {
            log.warn("Session capacity reached, rejecting new session owner={}", LogIds.subject(ownerSubject));
            throw new TravelSessionException(Reason.CAPACITY_REACHED);
        }
        outbox.afterCommit(events);
        log.info("Travel session created sessionId={} owner={}", session.sessionId(), LogIds.subject(ownerSubject));
        return new CreateTravelSessionResponse(session.sessionId(), TravelSessionStatus.ACTIVE, session.expiresAt());
    }

    public TravelSessionRecommendationResponse refresh(UUID sessionId, String ownerSubject, LocationRequest locationRequest) {
        return refresh(sessionId, ownerSubject, locationRequest, Set.of(), Set.of());
    }

    /**
     * Refreshes place recommendations and, when {@code eventTypes} is not empty, looks for live events of those
     * kinds that the traveler has not seen. Live alerts are best effort: their failure never fails the refresh.
     */
    public TravelSessionRecommendationResponse refresh(UUID sessionId, String ownerSubject, LocationRequest locationRequest,
                                                       Set<LiveEventDto.EventType> eventTypes, Set<String> seenEventIds) {
        Instant now = clock.instant();
        TravelSession session = requireActive(sessionId, ownerSubject, now);

        log.debug("Refresh requested sessionId={}", sessionId);
        if (!repository.tryBeginRefresh(session, now, refreshLease())) {
            log.info("Refresh rejected, another refresh is in progress sessionId={}", sessionId);
            throw new TravelSessionException(Reason.REFRESH_IN_PROGRESS);
        }
        try {
            Location current = new Location(locationRequest.latitude(), locationRequest.longitude());
            TravelSession.Snapshot snapshot = session.snapshot();

            if (!shouldRefresh(snapshot, current, now)) {
                // Skipped refreshes extend the session but do not move the movement baseline or cooldown clock.
                // Best effort: if another request changed the session meanwhile, its write wins.
                session.touch(now);
                repository.save(session, retention());
                log.info("Refresh skipped sessionId={} reason=movement-or-cooldown-not-met status=UNCHANGED", sessionId);
                return response(sessionId, RefreshStatus.UNCHANGED, List.of(), now, List.of());
            }

            // The provider call runs without any session lock held.
            RecommendationRequest recommendationRequest = new RecommendationRequest(
                    locationRequest,
                    snapshot.interests(),
                    snapshot.maxBudget(),
                    snapshot.currencyCode(),
                    snapshot.availableMinutes());
            Set<String> excluded = new HashSet<>(snapshot.recommendedPlaceIds());
            excluded.addAll(exclusions.excludedPlaceIds(ownerSubject));
            log.info("Calling recommendation service sessionId={} excludedPlaces={}", sessionId, excluded.size());
            RecommendationResponse result;
            try {
                result = recommendationService.recommend(recommendationRequest, excluded);
            } catch (AttractionProviderException ex) {
                log.warn("Refresh failed sessionId={} reason=recommendation-service-unavailable, session state unchanged",
                        sessionId);
                throw ex;
            }
            List<RecommendationDto> recommendations = result.recommendations();
            log.debug("Recommendation service returned sessionId={} count={}", sessionId, recommendations.size());
            List<String> placeIds = recommendations.stream()
                    .map(RecommendationDto::placeId)
                    .filter(Objects::nonNull)
                    .toList();

            // A stop or expiry that happened during the provider call discards this result.
            List<OutboxEntry> ready = placeIds.isEmpty() ? List.of() : outbox.prepare(EventType.RECOMMENDATIONS_READY,
                    new RecommendationsReady(sessionId.toString(), placeIds, "refresh"));
            Update recorded = update(session, s -> s.recordEvaluation(current, now, placeIds), ready);
            if (!recorded.applied()) {
                log.info("Refresh result discarded, session ended during provider call sessionId={}", sessionId);
                throw stateException(recorded.latest(), now);
            }
            outbox.afterCommit(ready);
            RefreshStatus status = recommendations.isEmpty() ? RefreshStatus.NO_NEW_RESULTS : RefreshStatus.UPDATED;
            List<LiveSuggestionDto> live = liveSuggestions(sessionId, locationRequest, snapshot.interests(), eventTypes,
                    seenEventIds);
            if (!live.isEmpty()) {
                outbox.publishStandalone(EventType.NEARBY_EVENT_DISCOVERED, new NearbyEventDiscovered(sessionId.toString(),
                        live.stream().map(item -> new NearbyEventDiscovered.Event(item.event().id(), item.event().name(),
                                item.event().type() == null ? null : item.event().type().name(), item.event().venueName(),
                                item.event().startsAt(), item.headline())).toList()));
            }
            log.info("Refresh completed sessionId={} status={} count={} liveSuggestions={}", sessionId, status,
                    recommendations.size(), live.size());
            return response(sessionId, status, recommendations, now, live);
        } finally {
            repository.endRefresh(session);
        }
    }

    /** Stopping is idempotent. Repeat calls on a stopped session succeed. Expired sessions cannot be stopped. */
    public void stop(UUID sessionId, String ownerSubject) {
        Instant now = clock.instant();
        TravelSession session = find(sessionId, ownerSubject);
        List<OutboxEntry> ended = outbox.prepare(EventType.TRAVEL_SESSION_ENDED,
                new TravelSessionEnded(sessionId.toString(), "STOPPED"));
        // Stopping an already stopped session publishes nothing.
        boolean wasActive = session.status() == TravelSessionStatus.ACTIVE;
        Update stopped = update(session, s -> {
            s.expireIfDue(now);
            if (s.status() == TravelSessionStatus.EXPIRED) {
                return false;
            }
            s.stop();
            return true;
        }, wasActive ? ended : List.of());
        if (stopped.applied() && wasActive) {
            outbox.afterCommit(ended);
        }
        if (!stopped.applied()) {
            persistExpiry(stopped.latest());
            log.info("Stop rejected, session already expired sessionId={}", sessionId);
            throw new TravelSessionException(Reason.EXPIRED);
        }
        log.info("Travel session stopped sessionId={} owner={}", sessionId, LogIds.subject(ownerSubject));
    }

    @Scheduled(fixedDelayString = "${travel.session.cleanup-interval-ms:60000}")
    public void purgeExpiredSessions() {
        int removed = repository.purge(clock.instant(), retention());
        if (removed > 0) {
            log.info("Purged expired travel sessions removed={}", removed);
        }
    }

    /**
     * Refresh only when the traveler has moved at least the minimum distance AND the cooldown has passed.
     * The first evaluation of a session always refreshes.
     */
    boolean shouldRefresh(TravelSession.Snapshot snapshot, Location current, Instant now) {
        if (snapshot.lastEvaluatedLocation() == null || snapshot.lastRecommendationTime() == null) {
            return true;
        }
        double movedMeters = DistanceCalculator.haversineKm(
                snapshot.lastEvaluatedLocation().latitude().doubleValue(),
                snapshot.lastEvaluatedLocation().longitude().doubleValue(),
                current.latitude().doubleValue(),
                current.longitude().doubleValue()) * 1000.0;
        boolean movedEnough = movedMeters >= properties.minimumMovementMeters();
        Instant cooldownEnds = snapshot.lastRecommendationTime()
                .plus(Duration.ofMinutes(properties.minimumRefreshIntervalMinutes()));
        boolean cooledDown = !now.isBefore(cooldownEnds);
        return movedEnough && cooledDown;
    }

    private TravelSession requireActive(UUID sessionId, String ownerSubject, Instant now) {
        TravelSession session = find(sessionId, ownerSubject);
        TravelSessionStatus before = session.status();
        session.expireIfDue(now);
        if (before == TravelSessionStatus.ACTIVE && session.status() == TravelSessionStatus.EXPIRED) {
            List<OutboxEntry> ended = outbox.prepare(EventType.TRAVEL_SESSION_ENDED,
                    new TravelSessionEnded(sessionId.toString(), "EXPIRED"));
            if (repository.save(session, retention(), ended)) {
                outbox.afterCommit(ended);
            }
        }
        if (session.status() != TravelSessionStatus.ACTIVE) {
            log.info("Request rejected, session is not active sessionId={} status={}", sessionId, session.status());
            throw stateException(session, now);
        }
        return session;
    }

    /**
     * Looks up a session the caller owns. Another user's session gets the same NOT_FOUND as a missing one,
     * so session IDs cannot be probed.
     */
    private TravelSession find(UUID sessionId, String ownerSubject) {
        TravelSession session = repository.findById(sessionId).orElseThrow(() -> {
            log.info("Session lookup found no session sessionId={}", sessionId);
            return new TravelSessionException(Reason.NOT_FOUND);
        });
        if (!session.ownerSubject().equals(ownerSubject)) {
            log.warn("Session access denied, caller does not own sessionId={}", sessionId);
            throw new TravelSessionException(Reason.NOT_FOUND);
        }
        return session;
    }

    /**
     * Applies {@code change} and saves it. If another request saved the session first, re-reads it and applies the
     * change again to the latest state, a few times at most. {@code change} returns false when it no longer
     * applies (for example the session has ended); nothing is saved then.
     */
    private Update update(TravelSession session, Predicate<TravelSession> change) {
        return update(session, change, List.of());
    }

    /** As {@link #update(TravelSession, Predicate)}, writing {@code events} atomically with the change. */
    private Update update(TravelSession session, Predicate<TravelSession> change, List<OutboxEntry> events) {
        TravelSession current = session;
        for (int attempt = 1; attempt <= MAX_UPDATE_ATTEMPTS; attempt++) {
            if (!change.test(current)) {
                return new Update(false, current);
            }
            if (repository.save(current, retention(), events)) {
                return new Update(true, current);
            }
            log.info("Session changed concurrently, retrying sessionId={} attempt={}", current.sessionId(), attempt);
            Optional<TravelSession> latest = repository.findById(current.sessionId());
            if (latest.isEmpty()) {
                return new Update(false, null);
            }
            current = latest.get();
        }
        log.warn("Session update gave up after {} concurrent changes sessionId={}", MAX_UPDATE_ATTEMPTS,
                session.sessionId());
        throw new TravelSessionException(Reason.CONCURRENT_UPDATE);
    }

    /** Writes an EXPIRED transition, which also wipes the stored preferences and location. Best effort. */
    private void persistExpiry(TravelSession session) {
        if (session != null && session.status() == TravelSessionStatus.EXPIRED) {
            repository.save(session, retention());
        }
    }

    private record Update(boolean applied, TravelSession latest) {
    }

    private TravelSessionException stateException(TravelSession session, Instant now) {
        if (session == null) {
            return new TravelSessionException(Reason.EXPIRED);
        }
        session.expireIfDue(now);
        return session.status() == TravelSessionStatus.STOPPED
                ? new TravelSessionException(Reason.STOPPED)
                : new TravelSessionException(Reason.EXPIRED);
    }

    private TravelSessionRecommendationResponse response(UUID sessionId, RefreshStatus status,
                                                         List<RecommendationDto> recommendations, Instant now,
                                                         List<LiveSuggestionDto> liveSuggestions) {
        return new TravelSessionRecommendationResponse(sessionId, status, recommendations, now, liveSuggestions);
    }

    /** Best effort, outside any session lock: an event-source or AI failure means no alerts this time. */
    private List<LiveSuggestionDto> liveSuggestions(UUID sessionId, LocationRequest location, Set<Interest> interests,
                                                    Set<LiveEventDto.EventType> eventTypes, Set<String> seenEventIds) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return List.of();
        }
        try {
            return recommendationService.liveSuggestions(new LiveSuggestionRequest(location, interests,
                    Set.copyOf(eventTypes), seenEventIds == null ? Set.of() : Set.copyOf(seenEventIds)));
        } catch (AttractionProviderException ex) {
            log.warn("Live suggestions unavailable sessionId={}, refresh continues without them", sessionId);
            return List.of();
        }
    }

    private Duration inactivityTimeout() {
        return Duration.ofMinutes(properties.inactivityTimeoutMinutes());
    }

    private Duration maxLifetime() {
        return Duration.ofMinutes(properties.maxLifetimeMinutes());
    }

    private Duration refreshLease() {
        return Duration.ofSeconds(properties.refreshLeaseSeconds());
    }

    private Duration retention() {
        return Duration.ofMinutes(properties.retentionAfterExpiryMinutes());
    }
}
