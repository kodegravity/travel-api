package com.aitravel.session.plan;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * Plans in the sessions table ({@code travel.session.store=dynamodb}), one item per traveler under
 * {@code plan#<owner hash>}. Session keys are UUIDs and outbox keys start with {@code outbox#}, so keys never
 * collide, and plan items have no {@code status}, so the session capacity scan never counts them.
 *
 * <p>Saves are conditional on {@code version}. Each save pushes {@code purgeAt} out by {@link #RETENTION}, so a plan
 * nobody has touched for a year is deleted by the table's TTL.
 */
public class DynamoDbTravelPlanStore implements TravelPlanStore {

    static final String KEY = "sessionId";
    static final String PREFIX = "plan#";
    static final String ENTRIES = "planEntries";
    static final String VERSION = "version";
    static final String SAVED_AT = "planSavedAt";
    static final String PURGE_AT = "purgeAt";
    static final Duration RETENTION = Duration.ofDays(365);

    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final Clock clock;

    public DynamoDbTravelPlanStore(DynamoDbClient dynamoDb, String tableName, Clock clock) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
        this.clock = clock;
    }

    @Override
    public Optional<StoredTravelPlan> find(String ownerKey) {
        GetItemResponse response = dynamoDb.getItem(r -> r.tableName(tableName).key(key(ownerKey)).consistentRead(true));
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        Map<String, AttributeValue> item = response.item();
        if (!clock.instant().isBefore(Instant.ofEpochSecond(Long.parseLong(item.get(PURGE_AT).n())))) {
            return Optional.empty();
        }
        return Optional.of(new StoredTravelPlan(Long.parseLong(item.get(VERSION).n()), item.get(ENTRIES).s(),
                Instant.ofEpochMilli(Long.parseLong(item.get(SAVED_AT).n()))));
    }

    @Override
    public boolean save(String ownerKey, long expectedVersion, StoredTravelPlan plan) {
        Map<String, AttributeValue> item = new HashMap<>(key(ownerKey));
        item.put(ENTRIES, AttributeValue.fromS(plan.entriesJson()));
        item.put(VERSION, AttributeValue.fromN(Long.toString(plan.version())));
        item.put(SAVED_AT, AttributeValue.fromN(Long.toString(plan.savedAt().toEpochMilli())));
        item.put(PURGE_AT, AttributeValue.fromN(Long.toString(plan.savedAt().plus(RETENTION).getEpochSecond())));
        // A purged-but-not-yet-deleted item counts as absent, matching find().
        String condition = expectedVersion == 0
                ? "attribute_not_exists(#key) OR #purge <= :now"
                : "#version = :expected AND #purge > :now";
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":now", AttributeValue.fromN(Long.toString(clock.instant().getEpochSecond())));
        Map<String, String> names = new HashMap<>(Map.of("#purge", PURGE_AT));
        if (expectedVersion == 0) {
            names.put("#key", KEY);
        } else {
            names.put("#version", VERSION);
            values.put(":expected", AttributeValue.fromN(Long.toString(expectedVersion)));
        }
        try {
            dynamoDb.putItem(r -> r.tableName(tableName).item(item).conditionExpression(condition)
                    .expressionAttributeNames(names).expressionAttributeValues(values));
            return true;
        } catch (ConditionalCheckFailedException ex) {
            return false;
        }
    }

    private static Map<String, AttributeValue> key(String ownerKey) {
        return Map.of(KEY, AttributeValue.fromS(PREFIX + ownerKey));
    }
}
