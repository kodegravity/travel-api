package com.aitravel.session.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aitravel.session.profile.SecretCredentialsDataSource.Credentials;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SecretCredentialsDataSourceTest {

    private final DataSource postgres = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final Deque<Credentials> secretVersions = new ArrayDeque<>(List.of(
            new Credentials("roam", "old"), new Credentials("roam", "rotated")));
    private int secretReads;
    private final SecretCredentialsDataSource dataSource = new SecretCredentialsDataSource(postgres, () -> {
        secretReads++;
        return secretVersions.size() > 1 ? secretVersions.poll() : secretVersions.peek();
    });

    @Test
    void readsTheSecretOnceForManyConnections() throws SQLException {
        when(postgres.getConnection("roam", "old")).thenReturn(connection);

        assertThat(dataSource.getConnection()).isSameAs(connection);
        assertThat(dataSource.getConnection()).isSameAs(connection);
        assertThat(secretReads).isEqualTo(1);
    }

    @Test
    void readsTheSecretAgainAfterARotation() throws SQLException {
        when(postgres.getConnection("roam", "old"))
                .thenThrow(new SQLException("password authentication failed", SecretCredentialsDataSource.INVALID_PASSWORD));
        when(postgres.getConnection("roam", "rotated")).thenReturn(connection);

        assertThat(dataSource.getConnection()).isSameAs(connection);
        assertThat(dataSource.getConnection()).isSameAs(connection);
        assertThat(secretReads).isEqualTo(2);
    }

    @Test
    void otherFailuresKeepTheCachedCredentials() throws SQLException {
        when(postgres.getConnection("roam", "old")).thenThrow(new SQLException("connection refused", "08001"));

        assertThatThrownBy(dataSource::getConnection).isInstanceOf(SQLException.class);
        assertThat(secretReads).isEqualTo(1);
    }

    @Test
    void neverPrintsThePassword() {
        assertThat(new Credentials("roam", "s3cret").toString()).doesNotContain("s3cret");
    }
}
