package com.aitravel.session.profile;

import com.aitravel.session.model.Interest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Profiles in the {@code traveler_profile} table ({@code db/migration}). Each call first makes sure the schema is
 * migrated, so a database that was down at startup is picked up once it is back.
 */
public class PostgresProfileStore implements ProfileStore {

    private static final String COLUMNS = "display_name, home_city, interests, currency, travel_mode, created_at, updated_at";

    private final JdbcClient jdbc;
    private final PostgresSchema schema;

    public PostgresProfileStore(JdbcClient jdbc, PostgresSchema schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    @Override
    public TravelerProfile touch(String ownerKey, Instant now) {
        return run(() -> jdbc.sql("""
                        insert into traveler_profile (owner_key, created_at, updated_at, last_seen_at)
                        values (:owner, :now, :now, :now)
                        on conflict (owner_key) do update set last_seen_at = excluded.last_seen_at
                        returning\s""" + COLUMNS)
                .param("owner", ownerKey)
                .param("now", Timestamp.from(now))
                .query(PostgresProfileStore::profile)
                .single());
    }

    @Override
    public TravelerProfile save(String ownerKey, ProfileFields fields, Instant now) {
        String[] interests = fields.interests().stream().map(Interest::name).sorted().toArray(String[]::new);
        return run(() -> jdbc.sql("""
                        insert into traveler_profile (owner_key, display_name, home_city, interests, currency,
                                                      travel_mode, created_at, updated_at, last_seen_at)
                        values (:owner, :displayName, :homeCity, :interests, :currency, :travelMode, :now, :now, :now)
                        on conflict (owner_key) do update set
                            display_name = excluded.display_name,
                            home_city = excluded.home_city,
                            interests = excluded.interests,
                            currency = excluded.currency,
                            travel_mode = excluded.travel_mode,
                            updated_at = excluded.updated_at,
                            last_seen_at = excluded.last_seen_at
                        returning\s""" + COLUMNS)
                .param("owner", ownerKey)
                .param("displayName", fields.displayName())
                .param("homeCity", fields.homeCity())
                .param("interests", interests)
                .param("currency", fields.currency())
                .param("travelMode", fields.travelMode())
                .param("now", Timestamp.from(now))
                .query(PostgresProfileStore::profile)
                .single());
    }

    private <T> T run(Supplier<T> call) {
        schema.ensureMigrated();
        try {
            return call.get();
        } catch (DataAccessException ex) {
            throw new ProfileUnavailableException(ex);
        }
    }

    private static TravelerProfile profile(ResultSet rs, int row) throws SQLException {
        return new TravelerProfile(
                rs.getString("display_name"),
                rs.getString("home_city"),
                interests((String[]) rs.getArray("interests").getArray()),
                rs.getString("currency"),
                rs.getString("travel_mode"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    /** Names no longer in {@link Interest} are dropped rather than failing the whole profile. */
    private static Set<Interest> interests(String[] names) {
        Set<Interest> interests = EnumSet.noneOf(Interest.class);
        Arrays.stream(names)
                .filter(name -> Arrays.stream(Interest.values()).anyMatch(interest -> interest.name().equals(name)))
                .forEach(name -> interests.add(Interest.valueOf(name)));
        return interests;
    }
}
