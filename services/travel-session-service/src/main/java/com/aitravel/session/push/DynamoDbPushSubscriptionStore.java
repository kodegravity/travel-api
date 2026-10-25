package com.aitravel.session.push;

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
 * Push subscriptions in the sessions table ({@code travel.session.store=dynamodb}), one item per traveler under
 * {@code push#<owner hash>}. Like plan items they have no {@code status}, so the session capacity scan never
 * counts them. Saves are conditional on {@code version}; each save pushes {@code purgeAt} out by {@link #RETENTION}.
 */
public class DynamoDbPushSubscriptionStore implements PushSubscriptionStore {

    static final String KEY = "sessionId";
    static final String PREFIX = "push#";
    static final String ENTRIES = "pushSubscriptions";
    static final String VERSION = "version";
    static final String SAVED_AT = "pushSavedAt";
    static final String PURGE_AT = "purgeAt";
    static final Duration RETENTION = Duration.ofDays(365);

    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final Clock clock;

    public DynamoDbPushSubscriptionStore(DynamoDbClient dynamoDb, String tableName, Clock clock) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
        this.clock = clock;
    }

    @Override
    public Optional<StoredPushSubscriptions> find(String ownerKey) {
        GetItemResponse response = dynamoDb.getItem(r -> r.tableName(tableName).key(key(ownerKey)).consistentRead(true));
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }
        Map<String, AttributeValue> item = response.item();
        if (!clock.instant().isBefore(Instant.ofEpochSecond(Long.parseLong(item.get(PURGE_AT).n())))) {
            return Optional.empty();
        }
        return Optional.of(new StoredPushSubscriptions(Long.parseLong(item.get(VERSION).n()), item.get(ENTRIES).s(),
                Instant.ofEpochMilli(Long.parseLong(item.get(SAVED_AT).n()))));
    }

    @Override
    public boolean save(String ownerKey, long expectedVersion, StoredPushSubscriptions subscriptions) {
        Map<String, AttributeValue> item = new HashMap<>(key(ownerKey));
        item.put(ENTRIES, AttributeValue.fromS(subscriptions.subscriptionsJson()));
        item.put(VERSION, AttributeValue.fromN(Long.toString(subscriptions.version())));
        item.put(SAVED_AT, AttributeValue.fromN(Long.toString(subscriptions.savedAt().toEpochMilli())));
        item.put(PURGE_AT, AttributeValue.fromN(Long.toString(subscriptions.savedAt().plus(RETENTION).getEpochSecond())));
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
