package com.aitravel.session.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.session.config.JwtTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.sns.SnsClient;

@SpringBootTest
@TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY,
        "TRAVEL_MESSAGING_ENABLED=true", "travel.messaging.topic-arn=arn:aws:sns:us-east-2:000000000000:roam-travel-events",
        "spring.cloud.aws.region.static=us-east-2", "spring.cloud.aws.credentials.access-key=x",
        "spring.cloud.aws.credentials.secret-key=x", "spring.cloud.aws.endpoint=http://127.0.0.1:9"})
/**
 * With messaging on and SQS unreachable (nothing listens on port 9), the service still starts: listeners start in the
 * background and keep retrying, so an SQS outage never takes the REST API down.
 */
class MessagingStartupTest {

    @BeforeAll
    static void region() {
        System.setProperty("aws.region", "us-east-2");
    }

    @Autowired EventOutbox outbox;
    @Autowired SnsClient sns;
    @Autowired NotificationEventListener listener;

    @Test
    void startsWithMessagingOnEvenWhenSqsIsUnreachable() {
        assertThat(outbox.enabled()).isTrue();
        assertThat(sns).isNotNull();
        assertThat(listener).isNotNull();
    }
}
