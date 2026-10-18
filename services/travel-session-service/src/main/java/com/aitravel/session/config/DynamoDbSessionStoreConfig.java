package com.aitravel.session.config;

import com.aitravel.session.repository.DynamoDbTravelSessionRepository;
import com.aitravel.session.repository.TravelSessionRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * Wires the DynamoDB session store when {@code travel.session.store=dynamodb}. Credentials and region come from the
 * AWS default chain (the ECS task role and {@code AWS_REGION} on Fargate) unless overridden here.
 */
@Configuration
@ConditionalOnProperty(prefix = "travel.session", name = "store", havingValue = "dynamodb")
@EnableConfigurationProperties(DynamoDbSessionStoreConfig.DynamoDbSessionProperties.class)
public class DynamoDbSessionStoreConfig {

    @Bean(destroyMethod = "close")
    DynamoDbClient travelSessionDynamoDbClient(DynamoDbSessionProperties properties) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(properties.connectTimeout())
                        .socketTimeout(properties.readTimeout()));
        if (!properties.region().isBlank()) {
            builder.region(Region.of(properties.region()));
        }
        if (!properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean
    TravelSessionRepository dynamoDbTravelSessionRepository(DynamoDbClient travelSessionDynamoDbClient,
                                                            DynamoDbSessionProperties properties, Clock clock) {
        return new DynamoDbTravelSessionRepository(travelSessionDynamoDbClient, properties.tableName(), clock);
    }

    /**
     * @param tableName      table holding sessions (partition key {@code sessionId}, TTL on {@code purgeAt})
     * @param region         AWS region; blank uses the default chain ({@code AWS_REGION})
     * @param endpoint       endpoint override, for DynamoDB Local; blank uses the regional endpoint
     * @param connectTimeout connection timeout for DynamoDB calls
     * @param readTimeout    response timeout for DynamoDB calls
     */
    @ConfigurationProperties("travel.session.dynamodb")
    public record DynamoDbSessionProperties(
            @DefaultValue("roam-travel-sessions") String tableName,
            @DefaultValue("") String region,
            @DefaultValue("") String endpoint,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("3s") Duration readTimeout
    ) {
    }
}
