package com.smartpay.event.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Atomic dedupe using the database primary key, safe with at-least-once Kafka delivery. */
@Service
public class EventDedupeService {

    private final EntityManager entityManager;

    public EventDedupeService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Returns true only for the first event id seen by this consumer group. */
    @Transactional
    public boolean markProcessed(String eventId, String consumerGroup) {
        int inserted = entityManager.createNativeQuery(
                        "INSERT IGNORE INTO processed_events (event_id, consumer_group, processed_at) "
                                + "VALUES (?, ?, ?)")
                .setParameter(1, eventId)
                .setParameter(2, consumerGroup)
                .setParameter(3, LocalDateTime.now())
                .executeUpdate();
        return inserted > 0;
    }
}
