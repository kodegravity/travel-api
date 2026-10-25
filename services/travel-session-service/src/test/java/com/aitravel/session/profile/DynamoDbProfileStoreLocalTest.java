package com.aitravel.session.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.session.model.Interest;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * The update expressions against a real DynamoDB. Skipped unless {@code DYNAMODB_TEST_ENDPOINT} is set, for example
 * {@code DYNAMODB_TEST_ENDPOINT=http://localhost:8000 ./mvnw test}.
 */
@EnabledIfEnvironmentVariable(named = "DYNAMODB_TEST_ENDPOINT", matches = ".+")
class DynamoDbProfileStoreLocalTest {

    private static final Instant FIRST_VISIT = Instant.parse("2026-10-25T12:00:00Z");

    private static DynamoDbClient client;
    private static String table;

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

    @Test
    void registersSavesAndKeepsAProfileAcrossVisits() {
        DynamoDbProfileStore store = new DynamoDbProfileStore(client, table);
        String owner = UUID.randomUUID().toString();

        TravelerProfile first = store.touch(owner, FIRST_VISIT);
        assertThat(first.travelMode()).isEqualTo("walk");
        assertThat(first.interests()).isEmpty();
        assertThat(first.createdAt()).isEqualTo(FIRST_VISIT);

        TravelerProfile saved = store.save(owner, new ProfileStore.ProfileFields("Sam", "Toronto",
                Set.of(Interest.FOOD), "CAD", "transit"), FIRST_VISIT.plusSeconds(60));
        assertThat(saved.displayName()).isEqualTo("Sam");
        assertThat(saved.createdAt()).isEqualTo(FIRST_VISIT);
        assertThat(saved.updatedAt()).isEqualTo(FIRST_VISIT.plusSeconds(60));

        TravelerProfile later = store.touch(owner, FIRST_VISIT.plusSeconds(3600));
        assertThat(later).isEqualTo(saved);

        TravelerProfile cleared = store.save(owner, new ProfileStore.ProfileFields(null, null, Set.of(), null, null),
                FIRST_VISIT.plusSeconds(7200));
        assertThat(cleared.displayName()).isNull();
        assertThat(cleared.homeCity()).isNull();
        assertThat(cleared.interests()).isEmpty();
        assertThat(cleared.travelMode()).isEqualTo("walk");
    }
}
