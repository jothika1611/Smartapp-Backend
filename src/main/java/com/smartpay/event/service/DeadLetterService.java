package com.smartpay.event.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.event.config.EventProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Publishes poison events to the DLQ with the original payload and a safe failure reason. */
@Service
public class DeadLetterService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventProperties properties;
    private final ObjectMapper objectMapper;

    public DeadLetterService(KafkaTemplate<String, String> kafkaTemplate,
                             EventProperties properties,
                             ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void publish(String originalTopic, String originalPayload, String reason) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "DEAD_LETTER");
        envelope.put("originalTopic", originalTopic);
        envelope.put("reason", truncate(reason));
        envelope.put("originalPayload", truncate(originalPayload));
        envelope.put("createdAt", LocalDateTime.now().toString());
        try {
            kafkaTemplate.send(properties.getTopics().getDlq(), "dlq",
                    objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialise DLQ event", exception);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 8000 ? value.substring(0, 8000) : value;
    }
}
