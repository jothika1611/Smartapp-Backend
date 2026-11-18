package com.smartpay.event.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.event.service.DeadLetterService;
import com.smartpay.event.service.EventDedupeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Creates user-facing notifications from committed transaction events. It never moves money. */
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    private static final String GROUP = "smartpay-notification";

    private final EventDedupeService dedupeService;
    private final DeadLetterService deadLetterService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(EventDedupeService dedupeService,
                                     DeadLetterService deadLetterService,
                                     JdbcTemplate jdbcTemplate,
                                     ObjectMapper objectMapper) {
        this.dedupeService = dedupeService;
        this.deadLetterService = deadLetterService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.tx-created}", groupId = GROUP)
    public void onTransactionCreated(String payload) {
        handle(payload, "CREATED");
    }

    @KafkaListener(topics = "${app.kafka.topics.tx-status}", groupId = GROUP)
    public void onTransactionStatusChanged(String payload) {
        handle(payload, "STATUS_CHANGED");
    }

    private void handle(String payload, String kind) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(payload);
            String eventId = envelope.path("eventId").asText();
            if (eventId.isBlank()) {
                throw new IllegalArgumentException("Missing eventId");
            }
            if (!dedupeService.markProcessed(eventId, GROUP)) {
                log.debug("Duplicate event {} skipped", eventId);
                return;
            }
            JsonNode body = envelope.path("payload");
            long transactionId = body.path("transactionId").asLong();
            String reference = body.path("reference").asText();
            long sender = body.path("senderUserId").asLong();
            long receiver = body.path("receiverUserId").asLong();
            if (transactionId <= 0 || sender <= 0 || receiver <= 0) {
                throw new IllegalArgumentException("Incomplete transaction event");
            }
            String title = "TRANSACTION_CREATED".equals(kind)
                    ? "Simulated transaction created"
                    : "Simulated transaction " + body.path("status").asText("updated");
            String message = "Reference " + reference + " — " + title
                    + ". No real money was moved.";
            insert(sender, transactionId, kind, title, message);
            insert(receiver, transactionId, kind, title, message);
        } catch (Exception exception) {
            deadLetterService.publish("notification-consumer:" + kind, payload, exception.getMessage());
            log.warn("Notification event sent to DLQ: {}", exception.getMessage());
        }
    }

    private void insert(Long userId, Long transactionId, String kind, String title, String message) {
        jdbcTemplate.update(
                "INSERT INTO notifications (user_id, transaction_id, type, title, message, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                userId, transactionId, "TX_" + kind, title, message, LocalDateTime.now());
    }
}
