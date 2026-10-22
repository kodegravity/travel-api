package com.aitravel.session.profile;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * Opens connections with credentials from a secret (the RDS-managed master user secret on AWS). The credentials are
 * cached; when the database rejects the password, as after a rotation, they are read again and the connection is
 * retried once. The connection pool sits in front of this, so the secret is read rarely.
 */
public class SecretCredentialsDataSource extends AbstractDataSource {

    private static final Logger log = LoggerFactory.getLogger(SecretCredentialsDataSource.class);

    /** PostgreSQL's SQLSTATE for a rejected password. */
    static final String INVALID_PASSWORD = "28P01";

    public record Credentials(String username, String password) {

        @Override
        public String toString() {
            return "Credentials[username=" + username + "]";
        }
    }

    private final DataSource target;
    private final Supplier<Credentials> secret;
    private volatile Credentials current;

    public SecretCredentialsDataSource(DataSource target, Supplier<Credentials> secret) {
        this.target = target;
        this.secret = secret;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Credentials credentials = current();
        try {
            return target.getConnection(credentials.username(), credentials.password());
        } catch (SQLException ex) {
            if (!INVALID_PASSWORD.equals(ex.getSQLState())) {
                throw ex;
            }
            log.info("Postgres rejected the cached password, reading the secret again");
            Credentials refreshed = secret.get();
            current = refreshed;
            return target.getConnection(refreshed.username(), refreshed.password());
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return target.getConnection(username, password);
    }

    private Credentials current() {
        Credentials credentials = current;
        if (credentials == null) {
            credentials = secret.get();
            current = credentials;
        }
        return credentials;
    }
}
