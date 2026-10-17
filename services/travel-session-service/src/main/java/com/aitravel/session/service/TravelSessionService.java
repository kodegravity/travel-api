package com.aitravel.session.service;

import com.aitravel.session.config.TravelSessionProperties;
import com.aitravel.session.dto.CreateTravelSessionRequest;
import com.aitravel.session.dto.CreateTravelSessionResponse;
import com.aitravel.session.dto.LocationRequest;
import com.aitravel.session.dto.RecommendationDto;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.dto.RefreshStatus;
import com.aitravel.session.dto.TravelSessionRecommendationResponse;
import com.aitravel.session.exception.TravelSessionException;
import com.aitravel.session.exception.TravelSessionException.Reason;
import com.aitravel.session.model.Location;
import com.aitravel.session.model.TravelSession;
import com.aitravel.session.model.TravelSessionStatus;
import com.aitravel.session.repository.TravelSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Orchestrates Active Travel Mode. Session state is updated under short per-session locks, and provider calls
 * run without holding any lock. A failed provider call leaves the session state unchanged.
 */
@Service
public class TravelSessionService {

    private static final Logger log = LoggerFactory.getLogger(TravelSessionService.class);

    private final TravelSessionRepository repository;
    private final RecommendationService recommendationService;
    private final TravelSessionProperties properties;
    private final Clock clock;

    public TravelSessionService(TravelSessionRepository repository,
                                RecommendationService recommendationService,
                                TravelSessionProperties properties,
                                Clock clock) {
        this.repository = repository;
        this.recommendationService = recommendationService;
        this.properties = properties;
        this.clock = clock;
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
                session.sessionId(), ownerSubject, request.maxBudget(), request.currencyCode(), session.expiresAt());
        if (!repository.addIfCapacity(session, properties.maxActiveSessions(), now, retention())) {
            log.warn("Session capacity reached, rejecting new session owner={}", ownerSubject);
            throw new TravelSessionException(Reason.CAPACITY_REACHED);
        }
        log.info("Travel session created sessionId={} owner={}", session.sessionId(), ownerSubject);
        return new CreateTravelSessionResponse(session.sessionId(), TravelSessionStatus.ACTIVE, session.expiresAt());
    }

    public TravelSessionRecommendationResponse refresh(UUID sessionId, String ownerSubject, LocationRequest locationRequest) {
        Instant now = clock.instant();
        TravelSession session = requireActive(sessionId, ownerSubject, now);

        log.debug("Refresh requested sessionId={}", sessionId);
        if (!session.tryBeginRefresh()) {
            log.info("Refresh rejected, another refresh is in progress sessionId={}", sessionId);
            throw new TravelSessionException(Reason.REFRESH_IN_PROGRESS);
        }
        try {
            Location current = new Location(locationRequest.latitude(), locationRequest.longitude());
            TravelSession.Snapshot snapshot = session.snapshot();

            if (!shouldRefresh(snapshot, current, now)) {
                // Skipped refreshes extend the session but do not move the movement baseline or cooldown clock.
                session.touch(now);
                log.info("Refresh skipped sessionId={} reason=movement-or-cooldown-not-met status=UNCHANGED", sessionId);
                return response(sessionId, RefreshStatus.UNCHANGED, List.of(), now);
            }

            // The provider call runs without any session lock held.
            RecommendationRequest recommendationRequest = new RecommendationRequest(
                    locationRequest,
                    snapshot.interests(),
                    snapshot.maxBudget(),
                    snapshot.currencyCode(),
                    snapshot.availableMinutes());
            log.info("Calling recommendation service sessionId={} excludedPlaces={}",
                    sessionId, snapshot.recommendedPlaceIds().size());
            RecommendationResponse result =
                    recommendationService.recommend(recommendationRequest, snapshot.recommendedPlaceIds());
            List<RecommendationDto> recommendations = result.recommendations();
            log.debug("Recommendation service returned sessionId={} count={}", sessionId, recommendations.size());
            List<String> placeIds = recommendations.stream()
                    .map(RecommendationDto::placeId)
                    .filter(Objects::nonNull)
                    .toList();

            // A stop or expiry that happened during the provider call discards this result.
            if (!session.recordEvaluation(current, now, placeIds)) {
                throw stateException(session, now);
            }
            RefreshStatus status = recommendations.isEmpty() ? RefreshStatus.NO_NEW_RESULTS : RefreshStatus.UPDATED;
            log.info("Refresh completed sessionId={} status={} count={}", sessionId, status, recommendations.size());
            return response(sessionId, status, recommendations, now);
        } finally {
            session.endRefresh();
        }
    }

    /** Stopping is idempotent. Repeat calls on a stopped session succeed. Expired sessions cannot be stopped. */
    public void stop(UUID sessionId, String ownerSubject) {
        Instant now = clock.instant();
        TravelSession session = find(sessionId, ownerSubject);
        session.expireIfDue(now);
        if (session.status() == TravelSessionStatus.EXPIRED) {
            throw new TravelSessionException(Reason.EXPIRED);
        }
        session.stop();
        log.info("Travel session stopped sessionId={} owner={}", sessionId, ownerSubject);
    }

    @Scheduled(fixedDelayString = "${travel.session.cleanup-interval-ms:60000}")
    public void purgeExpiredSessions() {
        repository.purge(clock.instant(), retention());
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
        session.expireIfDue(now);
        if (session.status() != TravelSessionStatus.ACTIVE) {
            throw stateException(session, now);
        }
        return session;
    }

    /**
     * Looks up a session the caller owns. Another user's session gets the same NOT_FOUND as a missing one,
     * so session IDs cannot be probed.
     */
    private TravelSession find(UUID sessionId, String ownerSubject) {
        return repository.findById(sessionId)
                .filter(session -> session.ownerSubject().equals(ownerSubject))
                .orElseThrow(() -> new TravelSessionException(Reason.NOT_FOUND));
    }

    private TravelSessionException stateException(TravelSession session, Instant now) {
        session.expireIfDue(now);
        return session.status() == TravelSessionStatus.STOPPED
                ? new TravelSessionException(Reason.STOPPED)
                : new TravelSessionException(Reason.EXPIRED);
    }

    private TravelSessionRecommendationResponse response(UUID sessionId, RefreshStatus status,
                                                         List<RecommendationDto> recommendations, Instant now) {
        return new TravelSessionRecommendationResponse(sessionId, status, recommendations, now);
    }

    private Duration inactivityTimeout() {
        return Duration.ofMinutes(properties.inactivityTimeoutMinutes());
    }

    private Duration maxLifetime() {
        return Duration.ofMinutes(properties.maxLifetimeMinutes());
    }

    private Duration retention() {
        return Duration.ofMinutes(properties.retentionAfterExpiryMinutes());
    }
}
