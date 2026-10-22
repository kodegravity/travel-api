package com.aitravel.session.config;

import com.aitravel.session.profile.PostgresProfileStore;
import com.aitravel.session.profile.PostgresSchema;
import com.aitravel.session.profile.ProfileStore;
import com.aitravel.session.profile.SecretCredentialsDataSource;
import com.aitravel.session.profile.SecretCredentialsDataSource.Credentials;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.function.Supplier;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires traveler profiles to PostgreSQL when {@code travel.postgres.enabled=true}. The pool starts without
 * connecting, and migrations run after startup, so the service starts even while the database is down. On AWS the
 * credentials come from the RDS-managed secret ({@code secret-id}) through the ECS task role.
 */
@Configuration
@ConditionalOnProperty(prefix = "travel.postgres", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PostgresConfig.PostgresProperties.class)
public class PostgresConfig {

    @Bean(destroyMethod = "close")
    HikariDataSource travelDataSource(PostgresProperties properties, JsonMapper mapper) {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setURL(properties.url());
        postgres.setConnectTimeout((int) properties.connectTimeout().toSeconds());
        postgres.setSocketTimeout((int) properties.queryTimeout().toSeconds());

        HikariConfig pool = new HikariConfig();
        pool.setPoolName("roam-postgres");
        pool.setMaximumPoolSize(properties.maxPoolSize());
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(properties.connectTimeout().toMillis());
        // Start without a connection: profiles answer 503 while the database is down; nothing else is affected.
        pool.setInitializationFailTimeout(-1);
        if (properties.secretId().isBlank()) {
            pool.setDataSource(postgres);
            pool.setUsername(properties.username());
            pool.setPassword(properties.password());
        } else {
            pool.setDataSource(new SecretCredentialsDataSource(postgres, secretCredentials(properties, mapper)));
        }
        return new HikariDataSource(pool);
    }

    @Bean
    PostgresSchema postgresSchema(HikariDataSource travelDataSource) {
        return new PostgresSchema(travelDataSource);
    }

    @Bean
    ProfileStore postgresProfileStore(HikariDataSource travelDataSource, PostgresSchema postgresSchema) {
        return new PostgresProfileStore(JdbcClient.create(travelDataSource), postgresSchema);
    }

    /** Reads the secret's JSON ({"username": ..., "password": ...}) each time it is asked. */
    private static Supplier<Credentials> secretCredentials(PostgresProperties properties, JsonMapper mapper) {
        return () -> {
            try (SecretsManagerClient secrets = SecretsManagerClient.builder()
                    .httpClientBuilder(UrlConnectionHttpClient.builder()
                            .connectionTimeout(properties.connectTimeout())
                            .socketTimeout(properties.queryTimeout()))
                    .build()) {
                String json = secrets.getSecretValue(request -> request.secretId(properties.secretId())).secretString();
                JsonNode secret = mapper.readTree(json);
                return new Credentials(secret.path("username").asString(), secret.path("password").asString());
            }
        };
    }

    /**
     * @param url            JDBC URL, for example {@code jdbc:postgresql://host:5432/roam?sslmode=require}
     * @param username       local development only; ignored when {@code secretId} is set
     * @param password       local development only; ignored when {@code secretId} is set
     * @param secretId       Secrets Manager secret holding {@code username} and {@code password}; blank to use the
     *                       two above
     * @param maxPoolSize    connections per task
     * @param connectTimeout to open a connection, and to wait for one from the pool
     * @param queryTimeout   socket read timeout for a statement
     */
    @ConfigurationProperties("travel.postgres")
    public record PostgresProperties(
            @DefaultValue("jdbc:postgresql://localhost:5432/roam") String url,
            @DefaultValue("") String username,
            @DefaultValue("") String password,
            @DefaultValue("") String secretId,
            @DefaultValue("5") int maxPoolSize,
            @DefaultValue("3s") Duration connectTimeout,
            @DefaultValue("5s") Duration queryTimeout
    ) {
    }
}
