package com.aitravel.session.profile;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Applies the Flyway migrations in {@code db/migration}. Tried once the service is ready and again on each profile
 * call until it succeeds, so a database that is down never stops the service from starting. Flyway's lock makes it
 * safe for several tasks to migrate at once.
 */
public class PostgresSchema {

    private static final Logger log = LoggerFactory.getLogger(PostgresSchema.class);

    private final Flyway flyway;
    private volatile boolean migrated;

    public PostgresSchema(DataSource dataSource) {
        this.flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @EventListener(ApplicationReadyEvent.class)
    void migrateAtStartup() {
        try {
            ensureMigrated();
        } catch (ProfileUnavailableException ex) {
            // Already logged; the first profile call tries again.
        }
    }

    /** @throws ProfileUnavailableException when the database cannot be reached or migrated */
    public void ensureMigrated() {
        if (migrated) {
            return;
        }
        synchronized (this) {
            if (migrated) {
                return;
            }
            try {
                int applied = flyway.migrate().migrationsExecuted;
                migrated = true;
                log.info("Postgres schema ready migrationsApplied={}", applied);
            } catch (RuntimeException ex) {
                log.warn("Postgres schema migration failed ({}), profiles unavailable until it succeeds",
                        ex.getClass().getSimpleName());
                throw new ProfileUnavailableException(ex);
            }
        }
    }
}
