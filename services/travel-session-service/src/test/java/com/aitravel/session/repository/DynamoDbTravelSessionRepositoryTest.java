package com.aitravel.session.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aitravel.session.config.TravelSessionProperties;
import com.aitravel.session.dto.CreateTravelSessionRequest;
import com.aitravel.session.dto.LocationRequest;
import com.aitravel.session.dto.RecommendationRequest;
import com.aitravel.session.dto.RecommendationResponse;
import com.aitravel.session.dto.RefreshStatus;
import com.aitravel.session.dto.TravelSessionRecommendationResponse;
import com.aitravel.session.exception.TravelSessionException;
import com.aitravel.session.exception.TravelSessionException.Reason;
import com.aitravel.session.model.Interest;
import com.aitravel.session.model.Location;
import com.aitravel.session.model.TravelSession;
import com.aitravel.session.model.TravelSessionStatus;
import com.aitravel.session.service.RecommendationService;
import com.aitravel.session.service.TravelSessionService;
import com.aitravel.session.support.MutableClock;
import com.aitravel.session.support.StubRecommendationService;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Runs against DynamoDB Local. Skipped unless {@code DYNAMODB_TEST_ENDPOINT} is set, for example:
 *
 * <pre>
 * docker run -d --rm -p 8000:8000 amazon/dynamodb-local:2.6.1 -jar DynamoDBLocal.jar -inMemory
 * DYNAMODB_TEST_ENDPOINT=http://localhost:8000 ./mvnw test
 * </pre>
 *
 * <p>Two repository instances on one table stand in for two ECS tasks.
 */
@EnabledIfEnvironmentVariable(named = "DYNAMODB_TEST_ENDPOINT", matches = ".+")
class DynamoDbTravelSessionRepositoryTest {

    private static final Duration INACTIVITY = Duration.ofMinutes(30);
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(120);
    private static final Duration RETENTION = Duration.ofMinutes(30);
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final String OWNER = "user-owner";
    private static final BigDecimal LAT = new BigDecimal("41.9028");
    private static final BigDecimal LON = new BigDecimal("12.4964");

    private static DynamoDbClient client;
    private static String table;

    private final MutableClock clock = new MutableClock();
    private DynamoDbTravelSessionRepository taskA;
    private DynamoDbTravelSessionRepository taskB;

    @BeforeAll
    static void createTable() {
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create(System.getenv("DYNAMODB_TEST_ENDPOINT")))
                .region(Region.US_EAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        table = "sessions-" + UUID.randomUUID();
        client.createTable(r -> r.tableName(table)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(a -> a.attributeName("sessionId").attributeType(ScalarAttributeType.S))
                .keySchema(k -> k.attributeName("sessionId").keyType(KeyType.HASH)));
    }

    @AfterAll
    static void dropTable() {
        client.deleteTable(r -> r.tableName(table));
        client.close();
    }

    @BeforeEach
    void setUp() {
        clock.reset();
        // Each test gets its own time window, so sessions from earlier tests count as expired.
        clock.advance(Duration.ofDays(testCounter++));
        taskA = new DynamoDbTravelSessionRepository(client, table, clock);
        taskB = new DynamoDbTravelSessionRepository(client, table, clock);
    }

    private static int testCounter;

    private TravelSession newSession() {
        return new TravelSession(UUID.randomUUID(), OWNER, Set.of(Interest.HISTORY, Interest.ART), new BigDecimal("50.00"),
                "EUR", 120, clock.instant(), INACTIVITY, MAX_LIFETIME);
    }

    @Test
    void roundTripsEveryFieldAcrossTasks() {
        TravelSession session = newSession();
        assertThat(taskA.addIfCapacity(session, 100, clock.instant(), RETENTION)).isTrue();
        session.recordEvaluation(new Location(LAT, LON), clock.instant(), List.of("colosseum", "pantheon"));
        assertThat(taskA.save(session, RETENTION)).isTrue();

        TravelSession read = taskB.findById(session.sessionId()).orElseThrow();

        assertThat(read.state()).isEqualTo(session.state());
        assertThat(read.snapshot().recommendedPlaceIds()).containsExactlyInAnyOrder("colosseum", "pantheon");
        assertThat(read.snapshot().lastEvaluatedLocation()).isEqualTo(new Location(LAT, LON));
    }

    @Test
    void staleCopyCannotOverwriteNewerChange() {
        TravelSession session = newSession();
        taskA.addIfCapacity(session, 100, clock.instant(), RETENTION);
        TravelSession copyA = taskA.findById(session.sessionId()).orElseThrow();
        TravelSession copyB = taskB.findById(session.sessionId()).orElseThrow();

        copyB.stop();
        assertThat(taskB.save(copyB, RETENTION)).isTrue();

        copyA.recordEvaluation(new Location(LAT, LON), clock.instant(), List.of("colosseum"));
        assertThat(taskA.save(copyA, RETENTION)).isFalse();
        assertThat(taskA.findById(session.sessionId()).orElseThrow().status()).isEqualTo(TravelSessionStatus.STOPPED);
    }

    @Test
    void stopRemovesPreferencesAndLocationFromTheStoredItem() {
        TravelSession session = newSession();
        taskA.addIfCapacity(session, 100, clock.instant(), RETENTION);
        session.recordEvaluation(new Location(LAT, LON), clock.instant(), List.of("colosseum"));
        taskA.save(session, RETENTION);

        session.stop();
        assertThat(taskA.save(session, RETENTION)).isTrue();

        Map<String, AttributeValue> item = client.getItem(r -> r.tableName(table)
                .key(Map.of("sessionId", AttributeValue.fromS(session.sessionId().toString())))).item();
        assertThat(item).doesNotContainKeys("lastLatitude", "lastLongitude", "interests", "maxBudget",
                "currencyCode", "availableMinutes", "recommendedPlaceIds");
        assertThat(item.get("status").s()).isEqualTo("STOPPED");
    }

    @Test
    void refreshLeaseIsExclusiveAcrossTasksAndExpires() {
        TravelSession session = newSession();
        taskA.addIfCapacity(session, 100, clock.instant(), RETENTION);

        assertThat(taskA.tryBeginRefresh(session, clock.instant(), LEASE)).isTrue();
        assertThat(taskB.tryBeginRefresh(session, clock.instant(), LEASE)).isFalse();

        clock.advance(LEASE.plusSeconds(1));
        assertThat(taskB.tryBeginRefresh(session, clock.instant(), LEASE)).isTrue();

        // The original holder's late release must not free the new holder's lease.
        taskA.endRefresh(session);
        assertThat(taskA.tryBeginRefresh(session, clock.instant(), LEASE)).isFalse();

        taskB.endRefresh(session);
        assertThat(taskA.tryBeginRefresh(session, clock.instant(), LEASE)).isTrue();
    }

    @Test
    void leaseDoesNotChangeTheVersion() {
        TravelSession session = newSession();
        taskA.addIfCapacity(session, 100, clock.instant(), RETENTION);
        taskA.tryBeginRefresh(session, clock.instant(), LEASE);

        session.touch(clock.instant());
        assertThat(taskA.save(session, RETENTION)).isTrue();
        taskA.endRefresh(session);
    }

    @Test
    void capacityCountsOnlyActiveUnexpiredSessions() {
        assertThat(taskA.addIfCapacity(newSession(), 2, clock.instant(), RETENTION)).isTrue();
        assertThat(taskB.addIfCapacity(newSession(), 2, clock.instant(), RETENTION)).isTrue();
        assertThat(taskA.addIfCapacity(newSession(), 2, clock.instant(), RETENTION)).isFalse();

        clock.advance(INACTIVITY.plusSeconds(1));
        assertThat(taskA.addIfCapacity(newSession(), 2, clock.instant(), RETENTION)).isTrue();
    }

    @Test
    void sessionsPastRetentionReadAsGoneBeforeTtlDeletesThem() {
        TravelSession session = newSession();
        taskA.addIfCapacity(session, 100, clock.instant(), RETENTION);

        clock.advance(INACTIVITY.plus(RETENTION).plusSeconds(1));

        assertThat(taskA.findById(session.sessionId())).isEmpty();
    }

    @Test
    void sessionServiceWorksAcrossTwoTasks() {
        TravelSessionProperties properties = new TravelSessionProperties(250, 5, 30, 120, 30, 100, 30);
        RecommendationService stub = new StubRecommendationService();
        TravelSessionService serviceA = new TravelSessionService(taskA, stub, properties, clock);
        TravelSessionService serviceB = new TravelSessionService(taskB, stub, properties, clock);

        UUID id = serviceA.create(new CreateTravelSessionRequest(Set.of(Interest.HISTORY), new BigDecimal("50"), "EUR",
                180), OWNER).sessionId();
        TravelSessionRecommendationResponse first = serviceB.refresh(id, OWNER, new LocationRequest(LAT, LON));
        assertThat(first.refreshStatus()).isEqualTo(RefreshStatus.UPDATED);

        clock.advance(Duration.ofMinutes(6));
        TravelSessionRecommendationResponse second = serviceA.refresh(id, OWNER,
                new LocationRequest(LAT.add(new BigDecimal("0.01")), LON));
        assertThat(second.recommendations()).extracting(r -> r.placeId())
                .doesNotContainAnyElementsOf(first.recommendations().stream().map(r -> r.placeId()).toList());

        serviceB.stop(id, OWNER);
        assertThatThrownBy(() -> serviceA.refresh(id, OWNER, new LocationRequest(LAT, LON)))
                .isInstanceOfSatisfying(TravelSessionException.class, ex -> assertThat(ex.reason()).isEqualTo(Reason.STOPPED));
        assertThatThrownBy(() -> serviceA.refresh(id, "someone-else", new LocationRequest(LAT, LON)))
                .isInstanceOfSatisfying(TravelSessionException.class, ex -> assertThat(ex.reason()).isEqualTo(Reason.NOT_FOUND));
    }

    @Test
    void stopOnAnotherTaskDiscardsAnInFlightRefresh() throws Exception {
        TravelSessionProperties properties = new TravelSessionProperties(250, 5, 30, 120, 30, 100, 30);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RecommendationService slow = new StubRecommendationService() {
            @Override
            public RecommendationResponse recommend(RecommendationRequest request, Set<String> excludedPlaceIds) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return super.recommend(request, excludedPlaceIds);
            }
        };
        TravelSessionService serviceA = new TravelSessionService(taskA, slow, properties, clock);
        TravelSessionService serviceB = new TravelSessionService(taskB, slow, properties, clock);
        UUID id = serviceA.create(new CreateTravelSessionRequest(Set.of(Interest.HISTORY), new BigDecimal("50"), "EUR",
                180), OWNER).sessionId();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<TravelSessionRecommendationResponse> inFlight =
                    executor.submit(() -> serviceA.refresh(id, OWNER, new LocationRequest(LAT, LON)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> serviceB.refresh(id, OWNER, new LocationRequest(LAT, LON)))
                    .isInstanceOfSatisfying(TravelSessionException.class,
                            ex -> assertThat(ex.reason()).isEqualTo(Reason.REFRESH_IN_PROGRESS));
            serviceB.stop(id, OWNER);
            release.countDown();

            assertThatThrownBy(inFlight::get).hasCauseInstanceOf(TravelSessionException.class);
            assertThat(taskB.findById(id).orElseThrow().snapshot().recommendedPlaceIds()).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }
}
