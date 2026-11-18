package com.smartpay.event.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.event.service.EventDedupeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Records poison events for manual inspection. Audit rows never alter financial state. */
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);
    private static final String GROUP = "smartpay-dlq-audit";

    private final EventDedupeService dedupeService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DeadLetterConsumer(EventDedupeService dedupeService,
                              JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper) {
        this.dedupeService = dedupeService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.dlq}", groupId = GROUP)
    public void onDeadLetter(String payload) {
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            String eventId = envelope.path("eventId").asText();
            if (eventId.isBlank()) {
                eventId = UUID.randomUUID().toString();
            }
            if (!dedupeService.markProcessed(eventId, GROUP)) {
                return;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("eventType", envelope.path("eventType").asText("UNKNOWN"));
            details.put("originalTopic", envelope.path("originalTopic").asText(""));
            details.put("reason", envelope.path("reason").asText(""));
            jdbcTemplate.update(
                    "INSERT INTO audit_logs (actor_user_id, action, target_type, target_id, details, created_at) "
                            + "VALUES (NULL, ?, ?, ?, ?, ?)",
                    "KAFKA_DLQ", "EVENT", eventId,
                    objectMapper.writeValueAsString(details), LocalDateTime.now());
            log.warn("Poison event recorded in audit_logs: {}", eventId);
        } catch (Exception exception) {
            log.error("Could not record DLQ event: {}", exception.getMessage());
        }
    }
}
