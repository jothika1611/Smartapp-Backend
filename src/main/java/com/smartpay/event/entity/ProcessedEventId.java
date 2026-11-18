package com.smartpay.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite key: an event is processed once per consumer group. */
@Embeddable
public class ProcessedEventId implements Serializable {

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    public ProcessedEventId() { }

    public ProcessedEventId(String eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
    }

    public String getEventId() { return eventId; }
    public String getConsumerGroup() { return consumerGroup; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProcessedEventId id)) return false;
        return Objects.equals(eventId, id.eventId)
                && Objects.equals(consumerGroup, id.consumerGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumerGroup);
    }
}
