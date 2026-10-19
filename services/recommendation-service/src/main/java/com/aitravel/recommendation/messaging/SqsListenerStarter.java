package com.aitravel.recommendation.messaging;

import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Starts the SQS listeners once the application is serving, and keeps retrying while SQS is unreachable or a queue
 * is missing. Listener containers are created with autoStartup off, so an SQS problem never stops the service from
 * starting or answering its REST API.
 */
public class SqsListenerStarter {

    private static final Logger log = LoggerFactory.getLogger(SqsListenerStarter.class);

    private final MessageListenerContainerRegistry registry;
    private final Duration retryInterval;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sqs-listener-starter");
        thread.setDaemon(true);
        return thread;
    });

    public SqsListenerStarter(MessageListenerContainerRegistry registry, Duration retryInterval) {
        this.registry = registry;
        this.retryInterval = retryInterval;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        scheduler.schedule(this::attempt, 0, TimeUnit.SECONDS);
    }

    /** Starts every listener not running yet; schedules another try while any could not start. */
    boolean attempt() {
        boolean allRunning = true;
        for (MessageListenerContainer<?> container : registry.getListenerContainers()) {
            if (container.isRunning()) {
                continue;
            }
            try {
                container.start();
                log.info("SQS listener started id={}", container.getId());
            } catch (RuntimeException ex) {
                allRunning = false;
                log.warn("SQS listener id={} could not start ({}); retrying in {}s", container.getId(),
                        ex.getClass().getSimpleName(), retryInterval.toSeconds());
            }
        }
        if (!allRunning && !scheduler.isShutdown()) {
            scheduler.schedule(this::attempt, retryInterval.toSeconds(), TimeUnit.SECONDS);
        }
        return allRunning;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
