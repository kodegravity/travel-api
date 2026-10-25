package com.aitravel.session.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import com.aitravel.session.support.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

/** Item layout and conditional writes, against a mocked client (DynamoDB Local is not needed). */
class DynamoDbFeedbackStoreTest {

    private final MutableClock clock = new MutableClock();
    private final DynamoDbClient dynamoDb = mock(DynamoDbClient.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
    private final DynamoDbFeedbackStore store = new DynamoDbFeedbackStore(dynamoDb, "sessions", clock);

    @Test
    void savesUnderItsOwnPrefixWithoutAStatus() {
        doReturn(PutItemResponse.builder().build()).when(dynamoDb).putItem(any(PutItemRequest.class));
        Instant now = clock.instant();

        assertThat(store.save("abc", 0, new StoredFeedback(1, "[]", now))).isTrue();

        ArgumentCaptor<PutItemRequest> put = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(put.capture());
        assertThat(put.getValue().item().get("sessionId").s()).isEqualTo("feedback#abc");
        assertThat(put.getValue().item().get("feedbackEntries").s()).isEqualTo("[]");
        assertThat(put.getValue().item()).doesNotContainKey("status");
        assertThat(put.getValue().conditionExpression()).startsWith("attribute_not_exists");
    }

    @Test
    void aFailedConditionIsAConflict() {
        doThrow(ConditionalCheckFailedException.builder().message("no").build())
                .when(dynamoDb).putItem(any(PutItemRequest.class));

        assertThat(store.save("abc", 2, new StoredFeedback(3, "[]", clock.instant()))).isFalse();
    }
}
