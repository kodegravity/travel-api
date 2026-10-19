package com.aitravel.session.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

class SnsEventPublisherTest {

    private final SnsClient sns = mock(SnsClient.class);

    @Test
    @SuppressWarnings("unchecked")
    void publishesTheEnvelopeWithTheEventTypeAttributeForFiltering() {
        when(sns.publish(any(Consumer.class))).thenReturn(PublishResponse.builder().messageId("m-1").build());

        new SnsEventPublisher(sns, "arn:aws:sns:us-east-2:000000000000:roam-travel-events")
                .publish(new OutboxEntry("e-1", "RecommendationsReady", "{\"eventId\":\"e-1\"}", Instant.now()));

        ArgumentCaptor<Consumer<PublishRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(sns).publish(captor.capture());
        PublishRequest.Builder builder = PublishRequest.builder();
        captor.getValue().accept(builder);
        PublishRequest request = builder.build();
        assertThat(request.topicArn()).endsWith(":roam-travel-events");
        assertThat(request.message()).isEqualTo("{\"eventId\":\"e-1\"}");
        assertThat(request.messageAttributes().get("eventType").stringValue()).isEqualTo("RecommendationsReady");
        assertThat(request.messageAttributes().get("eventType").dataType()).isEqualTo("String");
    }

    @Test
    void refusesToStartWithoutATopic() {
        assertThatThrownBy(() -> new SnsEventPublisher(sns, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRAVEL_MESSAGING_TOPIC_ARN");
    }
}
