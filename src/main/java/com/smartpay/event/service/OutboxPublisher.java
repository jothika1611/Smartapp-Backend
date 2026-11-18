package com.smartpay.event.service;

import com.smartpay.event.config.EventProperties;
import com.smartpay.event.entity.OutboxEvent;
import com.smartpay.event.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Polls committed outbox rows and publishes them at least once. Kafka is never in the DB tx. */
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String NEW = "NEW";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String FAILED = "FAILED";

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DeadLetterService deadLetterService;
    private final EventProperties properties;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           DeadLetterService deadLetterService,
                           EventProperties properties,
                           @Value("${app.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.deadLetterService = deadLetterService;
        this.properties = properties;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:2000}")
    public void publishBatch() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<OutboxEvent> events = repository
                    .findByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                            NEW, LocalDateTime.now(), PageRequest.of(0, batchSize));
            for (OutboxEvent event : events) {
                publishOne(event);
                repository.save(event);
            }
        } catch (Exception exception) {
            log.warn("Outbox poll failed: {}", exception.getMessage());
        } finally {
            running.set(false);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            event.setStatus(PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (Exception exception) {
            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(truncate(exception.getMessage()));
            if (event.getAttempts() >= properties.getOutbox().getMaxAttempts()) {
                deadLetterService.publish(event.getTopic(), event.getPayload(),
                        "Outbox retries exhausted: " + exception.getMessage());
                event.setStatus(FAILED);
                event.setNextAttemptAt(LocalDateTime.now());
            } else {
                long delayMs = properties.getOutbox().getRetryDelayMs()
                        * (1L << Math.min(event.getAttempts() - 1, 5));
                event.setNextAttemptAt(LocalDateTime.now().plusNanos(delayMs * 1_000_000L));
            }
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "unknown error";
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
