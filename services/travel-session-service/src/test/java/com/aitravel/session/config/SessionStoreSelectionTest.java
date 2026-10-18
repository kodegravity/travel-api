package com.aitravel.session.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.session.repository.DynamoDbTravelSessionRepository;
import com.aitravel.session.repository.InMemoryTravelSessionRepository;
import com.aitravel.session.repository.TravelSessionRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** The store is chosen by {@code travel.session.store}; building the DynamoDB client makes no AWS calls. */
class SessionStoreSelectionTest {

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
    @Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
    class Default {

        @Autowired
        private TravelSessionRepository repository;

        @Test
        void usesInMemoryStore() {
            assertThat(repository).isInstanceOf(InMemoryTravelSessionRepository.class);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY,
            "travel.session.store=dynamodb",
            "travel.session.dynamodb.region=us-east-2",
            "travel.session.dynamodb.endpoint=http://localhost:1"})
    @Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
    class DynamoDb {

        @Autowired
        private TravelSessionRepository repository;

        @Test
        void usesDynamoDbStore() {
            assertThat(repository).isInstanceOf(DynamoDbTravelSessionRepository.class);
        }
    }
}
