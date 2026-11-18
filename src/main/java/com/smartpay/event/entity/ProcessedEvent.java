package com.smartpay.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Dedupe marker written by an INSERT IGNORE when a consumer first sees an event id. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @EmbeddedId
    private ProcessedEventId id;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = LocalDateTime.now();
        }
    }

    public ProcessedEvent() { }

    public ProcessedEvent(String eventId, String consumerGroup) {
        this.id = new ProcessedEventId(eventId, consumerGroup);
    }

    public ProcessedEventId getId() { return id; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
