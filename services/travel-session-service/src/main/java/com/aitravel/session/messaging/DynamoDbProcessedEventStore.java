package com.aitravel.session.messaging;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Duplicate tracking shared by every task: a conditional put of {@code processed#<consumer>#<eventId>} into the
 * session table. The table's TTL on {@code purgeAt} removes the marker after {@code retention}.
 */
public class DynamoDbProcessedEventStore implements ProcessedEventStore {

    static final String KEY_PREFIX = "processed#";

    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final Duration retention;
    private final Clock clock;

    public DynamoDbProcessedEventStore(DynamoDbClient dynamoDb, String tableName, Duration retention, Clock clock) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
        this.retention = retention;
        this.clock = clock;
    }

    @Override
    public boolean markProcessed(String consumer, String eventId) {
        try {
            dynamoDb.putItem(r -> r.tableName(tableName)
                    .item(Map.of(
                            "sessionId", AttributeValue.fromS(KEY_PREFIX + consumer + "#" + eventId),
                            "purgeAt", AttributeValue.fromN(Long.toString(clock.instant().plus(retention).getEpochSecond()))))
                    .conditionExpression("attribute_not_exists(sessionId)"));
            return true;
        } catch (ConditionalCheckFailedException ex) {
            return false;
        }
    }

    @Override
    public void release(String consumer, String eventId) {
        dynamoDb.deleteItem(r -> r.tableName(tableName)
                .key(Map.of("sessionId", AttributeValue.fromS(KEY_PREFIX + consumer + "#" + eventId))));
    }
}
