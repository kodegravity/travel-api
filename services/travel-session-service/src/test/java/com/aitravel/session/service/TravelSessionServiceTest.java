package com.aitravel.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aitravel.session.config.TravelSessionProperties;
import com.aitravel.session.dto.CreateTravelSessionRequest;
import com.aitravel.session.dto.CreateTravelSessionResponse;
import com.aitravel.session.dto.LocationRequest;
import com.aitravel.session.dto.RecommendationDto;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.dto.RefreshStatus;
import com.aitravel.session.dto.TravelSessionRecommendationResponse;
import com.aitravel.session.exception.AttractionProviderException;
import com.aitravel.session.exception.TravelSessionException;
import com.aitravel.session.exception.TravelSessionException.Reason;
import com.aitravel.session.model.Interest;
import com.aitravel.session.model.TravelSession;
import com.aitravel.session.model.TravelSessionStatus;
import com.aitravel.session.repository.InMemoryTravelSessionRepository;
import com.aitravel.session.support.MutableClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TravelSessionServiceTest {

    // Roughly 300 m and 100 m of latitude change respectively.
    private static final String OWNER = "user-owner";
    private static final String OTHER_OWNER = "user-other";
    private static final BigDecimal LAT = new BigDecimal("41.9028");
    private static final BigDecimal LON = new BigDecimal("12.4964");
    private static final BigDecimal LAT_300M = new BigDecimal("41.9055");
    private static final BigDecimal LAT_100M = new BigDecimal("41.9037");

    private final MutableClock clock = new MutableClock();
    private final RecommendationService recommendationService = mock(RecommendationService.class);
    private final TravelSessionProperties properties = defaultProperties(100);
    private InMemoryTravelSessionRepository repository;
    private TravelSessionService service;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        clock.reset();
        service = newService(properties);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private TravelSessionService newService(TravelSessionProperties props) {
        repository = new InMemoryTravelSessionRepository();
        return new TravelSessionService(repository, recommendationService, props, clock);
    }

    private static TravelSessionProperties defaultProperties(int maxActive) {
        return new TravelSessionProperties(250, 5, 30, 120, 30, maxActive, 30);
    }

    private UUID createSession() {
        CreateTravelSessionRequest request = new CreateTravelSessionRequest(
                Set.of(Interest.HISTORY, Interest.ART), new BigDecimal("50"), "EUR", 180);
        return service.create(request, OWNER).sessionId();
    }

    private static LocationRequest at(BigDecimal latitude) {
        return new LocationRequest(latitude, LON);
    }

    private static RecommendationDto place(String id) {
        return new RecommendationDto(id, "Place " + id, "HISTORY", null, null, null, 60, null, null, null);
    }

    private static RecommendationResponse places(String... ids) {
        return new RecommendationResponse(java.util.Arrays.stream(ids).map(TravelSessionServiceTest::place).toList());
    }

    private void providerReturns(RecommendationResponse response) {
        when(recommendationService.recommend(any(RecommendationRequest.class), anySet())).thenReturn(response);
    }

    private static void assertReason(Runnable action, Reason expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(TravelSessionException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(expected));
    }

    @Test
    void createReturnsActiveSessionExpiringAfterInactivityTimeout() {
        CreateTravelSessionResponse response = service.create(new CreateTravelSessionRequest(
                Set.of(Interest.HISTORY), BigDecimal.TEN, "EUR", 120), OWNER);

        assertThat(response.sessionId()).isNotNull();
        assertThat(response.status()).isEqualTo(TravelSessionStatus.ACTIVE);
        assertThat(response.expiresAt()).isEqualTo(MutableClock.START.plus(Duration.ofMinutes(30)));
    }

    @Test
    void firstRefreshAlwaysGeneratesRecommendations() {
        UUID id = createSession();
        providerReturns(places("a", "b", "c"));

        TravelSessionRecommendationResponse response = service.refresh(id, OWNER, at(LAT));

        assertThat(response.refreshStatus()).isEqualTo(RefreshStatus.UPDATED);
        assertThat(response.recommendations()).extracting(RecommendationDto::placeId).containsExactly("a", "b", "c");
        assertThat(response.sessionId()).isEqualTo(id);
        verify(recommendationService, times(1)).recommend(any(RecommendationRequest.class), anySet());
    }

    @Test
    void movementBelowMinimumReturnsUnchangedWithoutProviderCall() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));

        clock.advance(Duration.ofMinutes(10));
        TravelSessionRecommendationResponse response = service.refresh(id, OWNER, at(LAT_100M));

        assertThat(response.refreshStatus()).isEqualTo(RefreshStatus.UNCHANGED);
        assertThat(response.recommendations()).isEmpty();
        verify(recommendationService, times(1)).recommend(any(RecommendationRequest.class), anySet());
    }

    @Test
    void cooldownBelowFiveMinutesReturnsUnchangedWithoutProviderCall() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));

        clock.advance(Duration.ofMinutes(4));
        TravelSessionRecommendationResponse response = service.refresh(id, OWNER, at(LAT_300M));

        assertThat(response.refreshStatus()).isEqualTo(RefreshStatus.UNCHANGED);
        verify(recommendationService, times(1)).recommend(any(RecommendationRequest.class), anySet());
    }

    @Test
    void sufficientMovementAfterCooldownTriggersRefresh() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));

        clock.advance(Duration.ofMinutes(6));
        providerReturns(places("b"));
        TravelSessionRecommendationResponse response = service.refresh(id, OWNER, at(LAT_300M));

        assertThat(response.refreshStatus()).isEqualTo(RefreshStatus.UPDATED);
        assertThat(response.recommendations()).extracting(RecommendationDto::placeId).containsExactly("b");
        verify(recommendationService, times(2)).recommend(any(RecommendationRequest.class), anySet());
    }

    @Test
    void skippedRefreshDoesNotResetMovementBaseline() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));

        // 100 m from the baseline: skipped. The baseline must stay at LAT.
        clock.advance(Duration.ofMinutes(6));
        assertThat(service.refresh(id, OWNER, at(LAT_100M)).refreshStatus()).isEqualTo(RefreshStatus.UNCHANGED);

        // 300 m from the original baseline (still satisfied by the cooldown): refreshes.
        clock.advance(Duration.ofMinutes(1));
        providerReturns(places("b"));
        assertThat(service.refresh(id, OWNER, at(LAT_300M)).refreshStatus()).isEqualTo(RefreshStatus.UPDATED);
    }

    @Test
    void previouslyRecommendedPlacesAreExcludedFromNextRefresh() {
        UUID id = createSession();
        providerReturns(places("a", "b", "c"));
        service.refresh(id, OWNER, at(LAT));

        clock.advance(Duration.ofMinutes(6));
        providerReturns(places("d"));
        TravelSessionRecommendationResponse response = service.refresh(id, OWNER, at(LAT_300M));

        assertThat(response.recommendations()).extracting(RecommendationDto::placeId).containsExactly("d");
        verify(recommendationService).recommend(any(RecommendationRequest.class),
                org.mockito.ArgumentMatchers.eq(Set.of("a", "b", "c")));
    }

    @Test
    void noUnseenEligibleResultsReturnsNoNewResults() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));

        clock.advance(Duration.ofMinutes(6));
        providerReturns(places());
        TravelSessionRecommendationResponse response = service.refresh(id, OWNER, at(LAT_300M));

        assertThat(response.refreshStatus()).isEqualTo(RefreshStatus.NO_NEW_RESULTS);
        assertThat(response.recommendations()).isEmpty();
    }

    @Test
    void providerFailureDoesNotMarkRefreshSuccessful() {
        UUID id = createSession();
        when(recommendationService.recommend(any(RecommendationRequest.class), anySet()))
                .thenThrow(new AttractionProviderException("upstream down", null))
                .thenReturn(places("a"));

        assertThatThrownBy(() -> service.refresh(id, OWNER, at(LAT)))
                .isInstanceOf(AttractionProviderException.class);

        // The failed attempt left no baseline, so the retry is still a first refresh.
        TravelSessionRecommendationResponse retry = service.refresh(id, OWNER, at(LAT));
        assertThat(retry.refreshStatus()).isEqualTo(RefreshStatus.UPDATED);
        verify(recommendationService, times(2)).recommend(any(RecommendationRequest.class), anySet());
    }

    @Test
    void unknownSessionIsNotFound() {
        assertReason(() -> service.refresh(UUID.randomUUID(), OWNER, at(LAT)), Reason.NOT_FOUND);
        assertReason(() -> service.stop(UUID.randomUUID(), OWNER), Reason.NOT_FOUND);
    }

    @Test
    void expiredSessionIsGoneAndDoesNotCallProvider() {
        UUID id = createSession();
        clock.advance(Duration.ofMinutes(30));

        assertReason(() -> service.refresh(id, OWNER, at(LAT)), Reason.EXPIRED);
        verifyNoInteractions(recommendationService);
    }

    @Test
    void validActivityExtendsInactivityExpiry() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));

        clock.advance(Duration.ofMinutes(20));
        assertThat(service.refresh(id, OWNER, at(LAT)).refreshStatus()).isEqualTo(RefreshStatus.UNCHANGED);

        // 40 minutes since creation, but only 20 since the last activity: still active.
        clock.advance(Duration.ofMinutes(20));
        assertThat(service.refresh(id, OWNER, at(LAT)).refreshStatus()).isEqualTo(RefreshStatus.UNCHANGED);
    }

    @Test
    void absoluteLifetimeCapsContinuousActivity() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));

        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMinutes(20));
            service.refresh(id, OWNER, at(LAT));
        }
        clock.advance(Duration.ofMinutes(20));

        // 120 minutes since creation: the absolute lifetime has ended despite recent activity.
        assertReason(() -> service.refresh(id, OWNER, at(LAT)), Reason.EXPIRED);
    }

    @Test
    void stopClearsStateAndBlocksRefreshes() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));

        service.stop(id, OWNER);

        assertReason(() -> service.refresh(id, OWNER, at(LAT_300M)), Reason.STOPPED);
        verify(recommendationService, times(1)).recommend(any(RecommendationRequest.class), anySet());
    }

    @Test
    void repeatedStopSucceeds() {
        UUID id = createSession();
        service.stop(id, OWNER);
        service.stop(id, OWNER);
    }

    @Test
    void stoppingExpiredSessionReportsExpired() {
        UUID id = createSession();
        clock.advance(Duration.ofMinutes(31));
        assertReason(() -> service.stop(id, OWNER), Reason.EXPIRED);
    }

    @Test
    void stopDiscardsResultOfRefreshThatCompletesAfterStop() throws Exception {
        UUID id = createSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(recommendationService.recommend(any(RecommendationRequest.class), anySet())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return places("a");
        });

        executor = Executors.newSingleThreadExecutor();
        Future<TravelSessionRecommendationResponse> inFlight = executor.submit(() -> service.refresh(id, OWNER, at(LAT)));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        service.stop(id, OWNER);
        release.countDown();

        assertThatThrownBy(inFlight::get).hasCauseInstanceOf(TravelSessionException.class);
    }

    @Test
    void overlappingRefreshOnSameSessionIsRejected() throws Exception {
        UUID id = createSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(recommendationService.recommend(any(RecommendationRequest.class), anySet())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return places("a");
        });

        executor = Executors.newSingleThreadExecutor();
        Future<TravelSessionRecommendationResponse> first = executor.submit(() -> service.refresh(id, OWNER, at(LAT)));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        assertReason(() -> service.refresh(id, OWNER, at(LAT)), Reason.REFRESH_IN_PROGRESS);

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).refreshStatus()).isEqualTo(RefreshStatus.UPDATED);
    }

    @Test
    void slowProviderCallForOneSessionDoesNotBlockAnotherSession() throws Exception {
        UUID slow = createSession();
        UUID fast = createSession();
        CountDownLatch slowEntered = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        when(recommendationService.recommend(any(RecommendationRequest.class), anySet())).thenAnswer(invocation -> {
            if (firstCall.compareAndSet(true, false)) {
                slowEntered.countDown();
                releaseSlow.await(5, TimeUnit.SECONDS);
            }
            return places("a");
        });

        executor = Executors.newFixedThreadPool(2);
        Future<TravelSessionRecommendationResponse> blocked = executor.submit(() -> service.refresh(slow, OWNER, at(LAT)));
        assertThat(slowEntered.await(5, TimeUnit.SECONDS)).isTrue();

        TravelSessionRecommendationResponse other = service.refresh(fast, OWNER, at(LAT));
        assertThat(other.refreshStatus()).isEqualTo(RefreshStatus.UPDATED);

        releaseSlow.countDown();
        assertThat(blocked.get(5, TimeUnit.SECONDS).refreshStatus()).isEqualTo(RefreshStatus.UPDATED);
    }

    @Test
    void sessionStateIsClearedAfterStop() {
        UUID id = createSession();
        providerReturns(places("a"));
        service.refresh(id, OWNER, at(LAT));
        TravelSession session = fetch(id);

        service.stop(id, OWNER);

        TravelSession.Snapshot snapshot = session.snapshot();
        assertThat(snapshot.interests()).isEmpty();
        assertThat(snapshot.lastEvaluatedLocation()).isNull();
        assertThat(snapshot.recommendedPlaceIds()).isEmpty();
        assertThat(session.status()).isEqualTo(TravelSessionStatus.STOPPED);
    }

    @Test
    void capacityLimitRejectsNewSessionsUntilOneEnds() {
        service = newService(defaultProperties(1));
        UUID first = createSession();

        assertReason(this::createSession, Reason.CAPACITY_REACHED);

        service.stop(first, OWNER);
        assertThat(createSession()).isNotNull();
    }

    @Test
    void purgeRemovesSessionsAfterRetentionWindow() {
        TravelSessionService local = new TravelSessionService(
                new InMemoryTravelSessionRepository(), recommendationService, properties, clock);
        UUID id = local.create(new CreateTravelSessionRequest(Set.of(Interest.ART), BigDecimal.ONE, "EUR", 60), OWNER)
                .sessionId();

        clock.advance(Duration.ofMinutes(30));
        local.purgeExpiredSessions();
        assertReason(() -> local.stop(id, OWNER), Reason.EXPIRED);

        clock.advance(Duration.ofMinutes(30));
        local.purgeExpiredSessions();
        assertReason(() -> local.stop(id, OWNER), Reason.NOT_FOUND);
    }

    private TravelSession fetch(UUID id) {
        return repository.findById(id).orElseThrow();
    }

    @Test
    void otherOwnersCannotRefreshOrStopSession() {
        UUID id = createSession();
        providerReturns(places("a", "b", "c"));

        assertReason(() -> service.refresh(id, OTHER_OWNER, at(LAT)), Reason.NOT_FOUND);
        assertReason(() -> service.stop(id, OTHER_OWNER), Reason.NOT_FOUND);
        assertThat(service.refresh(id, OWNER, at(LAT)).refreshStatus()).isEqualTo(RefreshStatus.UPDATED);
    }
}
