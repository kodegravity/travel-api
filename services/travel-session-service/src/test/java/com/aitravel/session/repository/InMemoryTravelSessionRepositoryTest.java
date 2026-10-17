package com.aitravel.session.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.session.model.Interest;
import com.aitravel.session.model.TravelSession;
import com.aitravel.session.model.TravelSessionStatus;
import com.aitravel.session.support.MutableClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class InMemoryTravelSessionRepositoryTest {

    private static final Duration INACTIVITY = Duration.ofMinutes(30);
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(120);
    private static final Duration RETENTION = Duration.ofMinutes(30);

    private final MutableClock clock = new MutableClock();
    private final InMemoryTravelSessionRepository repository = new InMemoryTravelSessionRepository();

    private TravelSession newSession() {
        return new TravelSession(UUID.randomUUID(), "user-owner", Set.of(Interest.HISTORY), BigDecimal.TEN, "EUR", 120,
                clock.instant(), INACTIVITY, MAX_LIFETIME);
    }

    @Test
    void storesAndFindsSessionById() {
        TravelSession session = newSession();
        assertThat(repository.addIfCapacity(session, 5, clock.instant(), RETENTION)).isTrue();
        assertThat(repository.findById(session.sessionId())).containsSame(session);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejectsSessionsBeyondActiveCapacity() {
        assertThat(repository.addIfCapacity(newSession(), 2, clock.instant(), RETENTION)).isTrue();
        assertThat(repository.addIfCapacity(newSession(), 2, clock.instant(), RETENTION)).isTrue();
        assertThat(repository.addIfCapacity(newSession(), 2, clock.instant(), RETENTION)).isFalse();
    }

    @Test
    void expiredSessionsFreeCapacityWithoutWaitingForPurge() {
        repository.addIfCapacity(newSession(), 1, clock.instant(), RETENTION);
        clock.advance(INACTIVITY.plusSeconds(1));
        assertThat(repository.addIfCapacity(newSession(), 1, clock.instant(), RETENTION)).isTrue();
    }

    @Test
    void purgeKeepsExpiredTombstoneUntilRetentionElapses() {
        TravelSession session = newSession();
        repository.addIfCapacity(session, 5, clock.instant(), RETENTION);

        clock.advance(INACTIVITY);
        assertThat(repository.purge(clock.instant(), RETENTION)).isZero();
        assertThat(repository.findById(session.sessionId()))
                .hasValueSatisfying(s -> assertThat(s.status()).isEqualTo(TravelSessionStatus.EXPIRED));

        clock.advance(RETENTION);
        assertThat(repository.purge(clock.instant(), RETENTION)).isEqualTo(1);
        assertThat(repository.findById(session.sessionId())).isEmpty();
    }

    @Test
    void concurrentCreatesNeverExceedCapacity() throws Exception {
        int capacity = 5;
        int attempts = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit((Callable<Boolean>) () -> {
                    start.await();
                    return repository.addIfCapacity(newSession(), capacity, clock.instant(), RETENTION);
                }));
            }
            start.countDown();
            long accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(capacity);
        } finally {
            pool.shutdownNow();
        }
    }
}
