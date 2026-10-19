package com.aitravel.recommendation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqsListenerStarterTest {

    @Test
    @SuppressWarnings("unchecked")
    void retriesListenersThatCannotStartAndLeavesRunningOnesAlone() {
        MessageListenerContainer<Object> failing = mock(MessageListenerContainer.class);
        MessageListenerContainer<Object> running = mock(MessageListenerContainer.class);
        when(running.isRunning()).thenReturn(true);
        doThrow(new IllegalStateException("queue not reachable")).doNothing().when(failing).start();
        MessageListenerContainerRegistry registry = mock(MessageListenerContainerRegistry.class);
        when(registry.getListenerContainers()).thenReturn(List.of(failing, running));
        SqsListenerStarter starter = new SqsListenerStarter(registry, Duration.ofHours(1));

        assertThat(starter.attempt()).isFalse();
        assertThat(starter.attempt()).isTrue();
        verify(running, never()).start();
        starter.shutdown();
    }
}
