package com.aitravel.session.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.session.model.Interest;
import com.aitravel.session.profile.ProfileStore.ProfileFields;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Runs against a real PostgreSQL. Skipped unless {@code POSTGRES_TEST_URL} is set, for example:
 * {@code POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/roam POSTGRES_TEST_USER=roam POSTGRES_TEST_PASSWORD=...
 * ./mvnw test -Dtest=PostgresProfileStoreTest}. CI runs it against a postgres service container.
 */
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresProfileStoreTest {

    private static HikariDataSource dataSource;
    private static PostgresProfileStore store;
    private static JdbcClient jdbc;

    private final String owner = UUID.randomUUID().toString().replace("-", "").repeat(2);
    private final Instant monday = Instant.parse("2026-10-19T09:00:00Z");

    @BeforeAll
    static void connect() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(System.getenv("POSTGRES_TEST_URL"));
        dataSource.setUsername(System.getenv().getOrDefault("POSTGRES_TEST_USER", "roam"));
        dataSource.setPassword(System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", ""));
        jdbc = JdbcClient.create(dataSource);
        PostgresSchema schema = new PostgresSchema(dataSource);
        schema.ensureMigrated();
        store = new PostgresProfileStore(jdbc, schema);
    }

    @AfterAll
    static void close() {
        dataSource.close();
    }

    @Test
    void createsAProfileTheFirstTimeATravelerIsSeen() {
        TravelerProfile profile = store.touch(owner, monday);

        assertThat(profile).isEqualTo(TravelerProfile.newTraveler(monday));
        assertThat(lastSeen()).isEqualTo(monday);
    }

    @Test
    void laterVisitsOnlyMoveLastSeen() {
        store.save(owner, fields("Sam", Set.of(Interest.FOOD)), monday);
        Instant tuesday = monday.plus(1, ChronoUnit.DAYS);

        TravelerProfile profile = store.touch(owner, tuesday);

        assertThat(profile.displayName()).isEqualTo("Sam");
        assertThat(profile.updatedAt()).isEqualTo(monday);
        assertThat(lastSeen()).isEqualTo(tuesday);
    }

    @Test
    void savingReplacesFieldsAndKeepsCreatedAt() {
        store.touch(owner, monday);
        Instant later = monday.plus(2, ChronoUnit.HOURS);

        TravelerProfile saved = store.save(owner, fields("Sam", Set.of(Interest.ART, Interest.NATURE)), later);
        TravelerProfile cleared = store.save(owner, new ProfileFields(null, null, Set.of(), null, "drive"), later);

        assertThat(saved.interests()).containsExactlyInAnyOrder(Interest.ART, Interest.NATURE);
        assertThat(saved.currency()).isEqualTo("CAD");
        assertThat(saved.createdAt()).isEqualTo(monday);
        assertThat(saved.updatedAt()).isEqualTo(later);
        assertThat(cleared).isEqualTo(new TravelerProfile(null, null, Set.of(), null, "drive", monday, later));
    }

    @Test
    void dropsInterestsTheAppNoLongerKnows() {
        store.save(owner, fields("Sam", Set.of(Interest.FOOD)), monday);
        jdbc.sql("update traveler_profile set interests = array['FOOD', 'RETIRED'] where owner_key = ?")
                .param(owner).update();

        assertThat(store.touch(owner, monday).interests()).containsExactly(Interest.FOOD);
    }

    @Test
    void migratingAgainChangesNothing() {
        new PostgresSchema(dataSource).ensureMigrated();

        assertThat(jdbc.sql("select count(*) from flyway_schema_history where success").query(Integer.class).single())
                .isEqualTo(1);
    }

    private ProfileFields fields(String name, Set<Interest> interests) {
        return new ProfileFields(name, "Toronto", interests, "CAD", "transit");
    }

    private Instant lastSeen() {
        return jdbc.sql("select last_seen_at from traveler_profile where owner_key = ?")
                .param(owner)
                .query((rs, row) -> rs.getTimestamp(1).toInstant())
                .single();
    }
}
