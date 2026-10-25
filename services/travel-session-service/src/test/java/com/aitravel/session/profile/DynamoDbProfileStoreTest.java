package com.aitravel.session.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import com.aitravel.session.model.Interest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

/** Item layout and update expressions, against a mocked client (DynamoDB Local is not needed). */
class DynamoDbProfileStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private final DynamoDbClient dynamoDb = mock(DynamoDbClient.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
    private final DynamoDbProfileStore store = new DynamoDbProfileStore(dynamoDb, "sessions");

    @Test
    void touchRegistersANewTravelerWithoutOverwritingAnExistingProfile() {
        returns(Map.of(
                "travelMode", AttributeValue.fromS("walk"),
                "interests", AttributeValue.fromL(List.of()),
                "profileCreatedAt", AttributeValue.fromN(Long.toString(NOW.toEpochMilli())),
                "profileUpdatedAt", AttributeValue.fromN(Long.toString(NOW.toEpochMilli()))));

        TravelerProfile profile = store.touch("abc", NOW);

        UpdateItemRequest update = captured();
        assertThat(update.key().get("sessionId").s()).isEqualTo("profile#abc");
        assertThat(update.updateExpression()).contains("#created = if_not_exists(#created, :now)")
                .contains("#travelMode = if_not_exists(#travelMode, :walk)")
                .doesNotContain("#displayName");
        assertThat(update.expressionAttributeNames()).doesNotContainKey("#displayName");
        assertThat(update.expressionAttributeValues().get(":purge").n())
                .isEqualTo(Long.toString(NOW.plus(DynamoDbProfileStore.RETENTION).getEpochSecond()));
        assertThat(profile.travelMode()).isEqualTo("walk");
        assertThat(profile.interests()).isEmpty();
        assertThat(profile.createdAt()).isEqualTo(NOW);
    }

    @Test
    void saveSetsGivenFieldsAndRemovesClearedOnes() {
        returns(Map.of(
                "displayName", AttributeValue.fromS("Sam"),
                "travelMode", AttributeValue.fromS("transit"),
                "interests", AttributeValue.fromL(List.of(AttributeValue.fromS("FOOD"), AttributeValue.fromS("GONE"))),
                "profileCreatedAt", AttributeValue.fromN(Long.toString(NOW.minusSeconds(60).toEpochMilli())),
                "profileUpdatedAt", AttributeValue.fromN(Long.toString(NOW.toEpochMilli()))));

        TravelerProfile saved = store.save("abc", new ProfileStore.ProfileFields("Sam", null,
                Set.of(Interest.FOOD, Interest.ART), null, "transit"), NOW);

        UpdateItemRequest update = captured();
        assertThat(update.updateExpression()).contains("#displayName = :displayName")
                .contains("REMOVE #homeCity, #currency");
        assertThat(update.expressionAttributeValues().get(":interests").l())
                .extracting(AttributeValue::s).containsExactly("ART", "FOOD");
        assertThat(saved.displayName()).isEqualTo("Sam");
        assertThat(saved.homeCity()).isNull();
        // An interest that no longer exists is dropped, not an error.
        assertThat(saved.interests()).containsExactly(Interest.FOOD);
        assertThat(saved.createdAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void aDynamoDbFailureIsAnUnavailableProfile() {
        doThrow(ProvisionedThroughputExceededException.builder().message("slow down").build())
                .when(dynamoDb).updateItem(any(UpdateItemRequest.class));

        assertThatThrownBy(() -> store.touch("abc", NOW)).isInstanceOf(ProfileUnavailableException.class);
    }

    private void returns(Map<String, AttributeValue> attributes) {
        doReturn(UpdateItemResponse.builder().attributes(attributes).build())
                .when(dynamoDb).updateItem(any(UpdateItemRequest.class));
    }

    private UpdateItemRequest captured() {
        ArgumentCaptor<UpdateItemRequest> update = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(update.capture());
        return update.getValue();
    }
}
