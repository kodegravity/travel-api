package com.aitravel.session.repository;

import com.aitravel.session.messaging.OutboxEntry;
import com.aitravel.session.messaging.OutboxStore;
import com.aitravel.session.model.Interest;
import com.aitravel.session.model.Location;
import com.aitravel.session.model.TravelSession;
import com.aitravel.session.model.TravelSessionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

/**
 * DynamoDB session store ({@code travel.session.store=dynamodb}). Sessions survive restarts and are shared by every
 * task, so any task can serve any request.
 *
 * <p>Table: partition key {@code sessionId} (S), on-demand capacity, TTL on {@code purgeAt}. Each read returns a new
 * copy. Writes are conditional on {@code version}, so a stale copy can never overwrite a newer change such as a stop.
 * The refresh lease lives in its own attributes and does not change the version.
 *
 * <p>DynamoDB deletes items past {@code purgeAt} in the background, usually within a few days; until then reads treat
 * them as gone. Stopped and expired items keep no preferences or location.
 *
 * <p>The capacity check counts active sessions with a scan, which is fine for a small table. It is not atomic across
 * tasks, so simultaneous creates can exceed the limit by a few.
 */
public class DynamoDbTravelSessionRepository implements TravelSessionRepository, OutboxStore {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTravelSessionRepository.class);

    static final String SESSION_ID = "sessionId";
    static final String OWNER = "ownerSubject";
    static final String CREATED_AT = "createdAt";
    static final String INACTIVITY_SECONDS = "inactivityTimeoutSeconds";
    static final String HARD_EXPIRES_AT = "hardExpiresAt";
    static final String INTERESTS = "interests";
    static final String MAX_BUDGET = "maxBudget";
    static final String CURRENCY = "currencyCode";
    static final String AVAILABLE_MINUTES = "availableMinutes";
    static final String STATUS = "status";
    static final String LAST_ACTIVITY_AT = "lastActivityAt";
    static final String EXPIRES_AT = "expiresAt";
    static final String LAST_LATITUDE = "lastLatitude";
    static final String LAST_LONGITUDE = "lastLongitude";
    static final String LAST_RECOMMENDATION_AT = "lastRecommendationAt";
    static final String RECOMMENDED_PLACE_IDS = "recommendedPlaceIds";
    static final String VERSION = "version";
    static final String PURGE_AT = "purgeAt";
    static final String LEASE_UNTIL = "refreshLeaseUntil";
    static final String LEASE_TOKEN = "refreshLeaseToken";
    /** Outbox items share the table under this key prefix; session keys are UUIDs, so they never collide. */
    static final String OUTBOX_PREFIX = "outbox#";
    static final String OUTBOX_TYPE = "outboxEventType";
    static final String OUTBOX_JSON = "outboxEnvelope";
    static final String OUTBOX_CREATED_AT = "outboxCreatedAt";
    /** Unpublished outbox items are kept at most this long (TTL), so a long outage cannot grow the table forever. */
    static final Duration OUTBOX_RETENTION = Duration.ofDays(7);

    /** Attributes that are absent when unknown or wiped; a save removes them when they have no value. */
    private static final List<String> OPTIONAL = List.of(INTERESTS, MAX_BUDGET, CURRENCY, AVAILABLE_MINUTES,
            LAST_LATITUDE, LAST_LONGITUDE, LAST_RECOMMENDATION_AT, RECOMMENDED_PLACE_IDS);

    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final Clock clock;
    private final Map<UUID, String> leaseTokens = new ConcurrentHashMap<>();

    public DynamoDbTravelSessionRepository(DynamoDbClient dynamoDb, String tableName, Clock clock) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
        this.clock = clock;
    }

    @Override
    public Optional<TravelSession> findById(UUID sessionId) {
        GetItemResponse response = dynamoDb.getItem(r -> r.tableName(tableName).key(key(sessionId)).consistentRead(true));
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        Map<String, AttributeValue> item = response.item();
        if (!clock.instant().isBefore(Instant.ofEpochSecond(number(item, PURGE_AT)))) {
            return Optional.empty();
        }
        return Optional.of(TravelSession.restore(fromItem(item)));
    }

    @Override
    public boolean addIfCapacity(TravelSession session, int maxActive, Instant now, Duration retentionAfterExpiry) {
        return addIfCapacity(session, maxActive, now, retentionAfterExpiry, List.of());
    }

    @Override
    public boolean addIfCapacity(TravelSession session, int maxActive, Instant now, Duration retentionAfterExpiry,
                                 List<OutboxEntry> outbox) {
        long active = countActive(now);
        if (active >= maxActive) {
            log.info("Session store at capacity active={} max={}", active, maxActive);
            return false;
        }
        TravelSession.State state = withVersion(session.state(), 1);
        Put put = Put.builder().tableName(tableName)
                .item(toItem(state, retentionAfterExpiry))
                .conditionExpression("attribute_not_exists(#id)")
                .expressionAttributeNames(Map.of("#id", SESSION_ID))
                .build();
        // A random UUID collision is practically impossible; refuse rather than overwrite.
        if (!write(TransactWriteItem.builder().put(put).build(), outbox)) {
            return false;
        }
        session.markSaved(1);
        return true;
    }

    @Override
    public boolean save(TravelSession session, Duration retentionAfterExpiry) {
        return save(session, retentionAfterExpiry, List.of());
    }

    @Override
    public boolean save(TravelSession session, Duration retentionAfterExpiry, List<OutboxEntry> outbox) {
        TravelSession.State state = session.state();
        long expected = state.version();
        long next = expected + 1;
        Map<String, AttributeValue> attributes = toItem(withVersion(state, next), retentionAfterExpiry);
        attributes.remove(SESSION_ID);

        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();
        List<String> sets = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
            names.put("#a" + i, entry.getKey());
            values.put(":v" + i, entry.getValue());
            sets.add("#a" + i + " = :v" + i);
            i++;
        }
        List<String> removes = new ArrayList<>();
        for (String optional : OPTIONAL) {
            if (!attributes.containsKey(optional)) {
                names.put("#r" + removes.size(), optional);
                removes.add("#r" + removes.size());
            }
        }
        names.put("#id", SESSION_ID);
        names.put("#ver", VERSION);
        values.put(":expected", AttributeValue.fromN(Long.toString(expected)));

        String update = "SET " + String.join(", ", sets) + (removes.isEmpty() ? "" : " REMOVE " + String.join(", ", removes));
        Update change = Update.builder().tableName(tableName)
                .key(key(state.sessionId()))
                .updateExpression(update)
                .conditionExpression("attribute_exists(#id) AND #ver = :expected")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build();
        if (!write(TransactWriteItem.builder().update(change).build(), outbox)) {
            return false;
        }
        session.markSaved(next);
        return true;
    }

    /**
     * Applies the session write alone, as before, or with the outbox entries in one transaction: both happen or
     * neither. False when the session's condition failed.
     */
    private boolean write(TransactWriteItem sessionWrite, List<OutboxEntry> outbox) {
        try {
            if (outbox.isEmpty()) {
                if (sessionWrite.put() != null) {
                    Put put = sessionWrite.put();
                    dynamoDb.putItem(r -> r.tableName(put.tableName()).item(put.item())
                            .conditionExpression(put.conditionExpression())
                            .expressionAttributeNames(put.expressionAttributeNames()));
                } else {
                    Update u = sessionWrite.update();
                    dynamoDb.updateItem(r -> r.tableName(u.tableName()).key(u.key())
                            .updateExpression(u.updateExpression())
                            .conditionExpression(u.conditionExpression())
                            .expressionAttributeNames(u.expressionAttributeNames())
                            .expressionAttributeValues(u.expressionAttributeValues()));
                }
                return true;
            }
            List<TransactWriteItem> items = new ArrayList<>();
            items.add(sessionWrite);
            outbox.forEach(entry -> items.add(TransactWriteItem.builder().put(outboxPut(entry)).build()));
            dynamoDb.transactWriteItems(r -> r.transactItems(items));
            return true;
        } catch (ConditionalCheckFailedException ex) {
            return false;
        } catch (TransactionCanceledException ex) {
            // The session's condition failing is the normal "someone else saved first"; anything else is an error.
            boolean conditionFailed = ex.hasCancellationReasons() && ex.cancellationReasons().stream()
                    .anyMatch(reason -> "ConditionalCheckFailed".equals(reason.code()));
            if (conditionFailed) {
                return false;
            }
            throw ex;
        }
    }

    private Put outboxPut(OutboxEntry entry) {
        return Put.builder().tableName(tableName)
                .item(Map.of(
                        SESSION_ID, AttributeValue.fromS(OUTBOX_PREFIX + entry.eventId()),
                        OUTBOX_TYPE, AttributeValue.fromS(entry.eventType()),
                        OUTBOX_JSON, AttributeValue.fromS(entry.json()),
                        OUTBOX_CREATED_AT, millis(entry.createdAt()),
                        PURGE_AT, AttributeValue.fromN(Long.toString(entry.createdAt().plus(OUTBOX_RETENTION).getEpochSecond()))))
                .conditionExpression("attribute_not_exists(#id)")
                .expressionAttributeNames(Map.of("#id", SESSION_ID))
                .build();
    }

    @Override
    public void addOutbox(List<OutboxEntry> entries) {
        for (OutboxEntry entry : entries) {
            Put put = outboxPut(entry);
            dynamoDb.putItem(r -> r.tableName(put.tableName()).item(put.item())
                    .conditionExpression(put.conditionExpression())
                    .expressionAttributeNames(put.expressionAttributeNames()));
        }
    }

    /** Outbox items are few and short-lived, so a filtered scan of this small table is cheap. */
    @Override
    public List<OutboxEntry> pendingOutbox(Instant olderThan, int limit) {
        List<OutboxEntry> found = new ArrayList<>();
        Map<String, AttributeValue> startKey = null;
        do {
            ScanRequest.Builder request = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("begins_with(#id, :prefix) AND #created < :cutoff")
                    .expressionAttributeNames(Map.of("#id", SESSION_ID, "#created", OUTBOX_CREATED_AT))
                    .expressionAttributeValues(Map.of(
                            ":prefix", AttributeValue.fromS(OUTBOX_PREFIX),
                            ":cutoff", millis(olderThan)));
            if (startKey != null) {
                request.exclusiveStartKey(startKey);
            }
            ScanResponse response = dynamoDb.scan(request.build());
            for (Map<String, AttributeValue> item : response.items()) {
                found.add(new OutboxEntry(item.get(SESSION_ID).s().substring(OUTBOX_PREFIX.length()),
                        item.get(OUTBOX_TYPE).s(), item.get(OUTBOX_JSON).s(),
                        Instant.ofEpochMilli(Long.parseLong(item.get(OUTBOX_CREATED_AT).n()))));
            }
            startKey = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                    ? response.lastEvaluatedKey() : null;
        } while (startKey != null && found.size() < limit);
        return found.stream().sorted(java.util.Comparator.comparing(OutboxEntry::createdAt)).limit(limit).toList();
    }

    @Override
    public void removeOutbox(String eventId) {
        dynamoDb.deleteItem(r -> r.tableName(tableName)
                .key(Map.of(SESSION_ID, AttributeValue.fromS(OUTBOX_PREFIX + eventId))));
    }

    @Override
    public boolean tryBeginRefresh(TravelSession session, Instant now, Duration lease) {
        String token = UUID.randomUUID().toString();
        try {
            dynamoDb.updateItem(r -> r.tableName(tableName)
                    .key(key(session.sessionId()))
                    .updateExpression("SET #until = :until, #token = :token")
                    .conditionExpression("attribute_exists(#id) AND (attribute_not_exists(#until) OR #until < :now)")
                    .expressionAttributeNames(Map.of("#id", SESSION_ID, "#until", LEASE_UNTIL, "#token", LEASE_TOKEN))
                    .expressionAttributeValues(Map.of(
                            ":until", AttributeValue.fromN(Long.toString(now.plus(lease).toEpochMilli())),
                            ":now", AttributeValue.fromN(Long.toString(now.toEpochMilli())),
                            ":token", AttributeValue.fromS(token))));
        } catch (ConditionalCheckFailedException ex) {
            return false;
        }
        leaseTokens.put(session.sessionId(), token);
        return true;
    }

    @Override
    public void endRefresh(TravelSession session) {
        String token = leaseTokens.remove(session.sessionId());
        if (token == null) {
            return;
        }
        try {
            // Only the holder releases the lease; one that already expired and was taken over is left alone.
            dynamoDb.updateItem(r -> r.tableName(tableName)
                    .key(key(session.sessionId()))
                    .updateExpression("REMOVE #until, #token")
                    .conditionExpression("#token = :token")
                    .expressionAttributeNames(Map.of("#until", LEASE_UNTIL, "#token", LEASE_TOKEN))
                    .expressionAttributeValues(Map.of(":token", AttributeValue.fromS(token))));
        } catch (ConditionalCheckFailedException ex) {
            log.info("Refresh lease already expired or taken over sessionId={}", session.sessionId());
        }
    }

    /** DynamoDB TTL on {@code purgeAt} removes old sessions, so there is nothing to do here. */
    @Override
    public int purge(Instant now, Duration retentionAfterExpiry) {
        return 0;
    }

    private long countActive(Instant now) {
        long count = 0;
        Map<String, AttributeValue> startKey = null;
        do {
            ScanRequest.Builder request = ScanRequest.builder()
                    .tableName(tableName)
                    .select(Select.COUNT)
                    .filterExpression("#st = :active AND #exp > :now")
                    .expressionAttributeNames(Map.of("#st", STATUS, "#exp", EXPIRES_AT))
                    .expressionAttributeValues(Map.of(
                            ":active", AttributeValue.fromS(TravelSessionStatus.ACTIVE.name()),
                            ":now", AttributeValue.fromN(Long.toString(now.toEpochMilli()))));
            if (startKey != null) {
                request.exclusiveStartKey(startKey);
            }
            ScanResponse response = dynamoDb.scan(request.build());
            count += response.count();
            startKey = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                    ? response.lastEvaluatedKey() : null;
        } while (startKey != null);
        return count;
    }

    private static Map<String, AttributeValue> key(UUID sessionId) {
        return Map.of(SESSION_ID, AttributeValue.fromS(sessionId.toString()));
    }

    static Map<String, AttributeValue> toItem(TravelSession.State s, Duration retentionAfterExpiry) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(SESSION_ID, AttributeValue.fromS(s.sessionId().toString()));
        item.put(OWNER, AttributeValue.fromS(s.ownerSubject()));
        item.put(CREATED_AT, millis(s.createdAt()));
        item.put(INACTIVITY_SECONDS, AttributeValue.fromN(Long.toString(s.inactivityTimeout().toSeconds())));
        item.put(HARD_EXPIRES_AT, millis(s.hardExpiresAt()));
        item.put(STATUS, AttributeValue.fromS(s.status().name()));
        item.put(LAST_ACTIVITY_AT, millis(s.lastActivityAt()));
        item.put(EXPIRES_AT, millis(s.expiresAt()));
        item.put(VERSION, AttributeValue.fromN(Long.toString(s.version())));
        item.put(PURGE_AT, AttributeValue.fromN(Long.toString(
                s.expiresAt().plus(retentionAfterExpiry).getEpochSecond())));
        if (!s.interests().isEmpty()) {
            item.put(INTERESTS, AttributeValue.fromSs(s.interests().stream().map(Enum::name).sorted().toList()));
        }
        if (s.maxBudget() != null) {
            item.put(MAX_BUDGET, AttributeValue.fromN(s.maxBudget().toPlainString()));
        }
        if (s.currencyCode() != null) {
            item.put(CURRENCY, AttributeValue.fromS(s.currencyCode()));
        }
        if (s.availableMinutes() != null) {
            item.put(AVAILABLE_MINUTES, AttributeValue.fromN(s.availableMinutes().toString()));
        }
        if (s.lastEvaluatedLocation() != null) {
            item.put(LAST_LATITUDE, AttributeValue.fromN(s.lastEvaluatedLocation().latitude().toPlainString()));
            item.put(LAST_LONGITUDE, AttributeValue.fromN(s.lastEvaluatedLocation().longitude().toPlainString()));
        }
        if (s.lastRecommendationTime() != null) {
            item.put(LAST_RECOMMENDATION_AT, millis(s.lastRecommendationTime()));
        }
        if (!s.recommendedPlaceIds().isEmpty()) {
            item.put(RECOMMENDED_PLACE_IDS, AttributeValue.fromSs(List.copyOf(s.recommendedPlaceIds())));
        }
        return item;
    }

    static TravelSession.State fromItem(Map<String, AttributeValue> item) {
        Set<Interest> interests = item.containsKey(INTERESTS)
                ? item.get(INTERESTS).ss().stream().map(Interest::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Interest.class)))
                : Set.of();
        Location location = item.containsKey(LAST_LATITUDE) && item.containsKey(LAST_LONGITUDE)
                ? new Location(new BigDecimal(item.get(LAST_LATITUDE).n()), new BigDecimal(item.get(LAST_LONGITUDE).n()))
                : null;
        return new TravelSession.State(
                UUID.fromString(item.get(SESSION_ID).s()),
                item.get(OWNER).s(),
                instant(item, CREATED_AT),
                Duration.ofSeconds(number(item, INACTIVITY_SECONDS)),
                instant(item, HARD_EXPIRES_AT),
                interests,
                item.containsKey(MAX_BUDGET) ? new BigDecimal(item.get(MAX_BUDGET).n()) : null,
                item.containsKey(CURRENCY) ? item.get(CURRENCY).s() : null,
                item.containsKey(AVAILABLE_MINUTES) ? Integer.valueOf(item.get(AVAILABLE_MINUTES).n()) : null,
                TravelSessionStatus.valueOf(item.get(STATUS).s()),
                instant(item, LAST_ACTIVITY_AT),
                instant(item, EXPIRES_AT),
                location,
                item.containsKey(LAST_RECOMMENDATION_AT) ? instant(item, LAST_RECOMMENDATION_AT) : null,
                item.containsKey(RECOMMENDED_PLACE_IDS) ? new HashSet<>(item.get(RECOMMENDED_PLACE_IDS).ss()) : Set.of(),
                number(item, VERSION));
    }

    private static TravelSession.State withVersion(TravelSession.State s, long version) {
        return new TravelSession.State(s.sessionId(), s.ownerSubject(), s.createdAt(), s.inactivityTimeout(),
                s.hardExpiresAt(), s.interests(), s.maxBudget(), s.currencyCode(), s.availableMinutes(), s.status(),
                s.lastActivityAt(), s.expiresAt(), s.lastEvaluatedLocation(), s.lastRecommendationTime(),
                s.recommendedPlaceIds(), version);
    }

    private static AttributeValue millis(Instant instant) {
        return AttributeValue.fromN(Long.toString(instant.toEpochMilli()));
    }

    private static Instant instant(Map<String, AttributeValue> item, String name) {
        return Instant.ofEpochMilli(number(item, name));
    }

    private static long number(Map<String, AttributeValue> item, String name) {
        return Long.parseLong(item.get(name).n());
    }
}
