package com.aitravel.recommendation.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.recommendation.config.JwtTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** With messaging on and SQS unreachable, the service still starts; listeners keep retrying in the background. */
@SpringBootTest
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY,
        "TRAVEL_MESSAGING_ENABLED=true", "spring.cloud.aws.region.static=us-east-2",
        "spring.cloud.aws.credentials.access-key=x", "spring.cloud.aws.credentials.secret-key=x",
        "spring.cloud.aws.endpoint=http://127.0.0.1:9"})
class MessagingStartupTest {

    @BeforeAll
    static void region() {
        System.setProperty("aws.region", "us-east-2");
    }

    @Autowired
    private SessionEventListener listener;

    @Test
    void startsWithMessagingOnEvenWhenSqsIsUnreachable() {
        assertThat(listener).isNotNull();
    }
}
