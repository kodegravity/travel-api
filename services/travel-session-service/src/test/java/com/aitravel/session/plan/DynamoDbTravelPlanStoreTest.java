package com.aitravel.session.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import com.aitravel.session.support.MutableClock;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

/** Item layout and conditional writes, against a mocked client (DynamoDB Local is not needed). */
class DynamoDbTravelPlanStoreTest {

    private final MutableClock clock = new MutableClock();
    // Real default methods turn the builder-style calls into the request-object calls stubbed below.
    private final DynamoDbClient dynamoDb = mock(DynamoDbClient.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
    private final DynamoDbTravelPlanStore store = new DynamoDbTravelPlanStore(dynamoDb, "sessions", clock);

    @Test
    void firstSaveRequiresThatNoPlanExists() {
        doReturn(PutItemResponse.builder().build()).when(dynamoDb).putItem(any(PutItemRequest.class));
        Instant now = clock.instant();

        assertThat(store.save("abc", 0, new StoredTravelPlan(1, "{}", now))).isTrue();

        ArgumentCaptor<PutItemRequest> put = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(put.capture());
        assertThat(put.getValue().item().get("sessionId").s()).isEqualTo("plan#abc");
        assertThat(put.getValue().item().get("version").n()).isEqualTo("1");
        assertThat(put.getValue().item()).doesNotContainKey("status");
        assertThat(put.getValue().item().get("purgeAt").n())
                .isEqualTo(Long.toString(now.plus(DynamoDbTravelPlanStore.RETENTION).getEpochSecond()));
        assertThat(put.getValue().conditionExpression()).startsWith("attribute_not_exists");
    }

    @Test
    void laterSavesRequireTheExpectedVersion() {
        doReturn(PutItemResponse.builder().build()).when(dynamoDb).putItem(any(PutItemRequest.class));

        store.save("abc", 4, new StoredTravelPlan(5, "{}", clock.instant()));

        ArgumentCaptor<PutItemRequest> put = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(put.capture());
        assertThat(put.getValue().conditionExpression()).contains("#version = :expected");
        assertThat(put.getValue().expressionAttributeValues().get(":expected").n()).isEqualTo("4");
    }

    @Test
    void aFailedConditionIsAConflict() {
        doThrow(ConditionalCheckFailedException.builder().message("no").build())
                .when(dynamoDb).putItem(any(PutItemRequest.class));

        assertThat(store.save("abc", 4, new StoredTravelPlan(5, "{}", clock.instant()))).isFalse();
    }

    @Test
    void readsAStoredPlanAndTreatsAPurgedOneAsGone() {
        Instant now = clock.instant();
        Map<String, AttributeValue> item = Map.of(
                "sessionId", AttributeValue.fromS("plan#abc"),
                "planEntries", AttributeValue.fromS("{\"favorites\":{}}"),
                "version", AttributeValue.fromN("3"),
                "planSavedAt", AttributeValue.fromN(Long.toString(now.toEpochMilli())),
                "purgeAt", AttributeValue.fromN(Long.toString(now.getEpochSecond() + 60)));
        doReturn(GetItemResponse.builder().item(item).build()).when(dynamoDb).getItem(any(GetItemRequest.class));

        assertThat(store.find("abc")).hasValueSatisfying(plan -> {
            assertThat(plan.version()).isEqualTo(3);
            assertThat(plan.entriesJson()).isEqualTo("{\"favorites\":{}}");
        });

        clock.advance(java.time.Duration.ofSeconds(61));
        assertThat(store.find("abc")).isEmpty();
    }
}
