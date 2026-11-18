package com.smartpay.event.service;

import com.smartpay.event.config.EventProperties;
import com.smartpay.event.entity.OutboxEvent;
import com.smartpay.event.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private DeadLetterService deadLetterService;

    private OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setId("event-1");
        event.setTopic("smartpay.tx.created");
        event.setPartitionKey("tx-1");
        event.setPayload("{\"eventId\":\"event-1\"}");
        event.setStatus("NEW");
        event.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        return event;
    }

    private OutboxPublisher publisher(EventProperties properties) {
        return new OutboxPublisher(repository, kafkaTemplate, deadLetterService, properties, 100);
    }

    @Test
    void publishesNewEventAndMarksItPublished() {
        EventProperties properties = new EventProperties();
        OutboxEvent event = event();
        when(repository.findByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                anyString(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher(properties).publishBatch();

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("PUBLISHED");
        assertThat(saved.getValue().getPublishedAt()).isNotNull();
        assertThat(saved.getValue().getAttempts()).isZero();
    }

    @Test
    void exhaustedRetriesSendToDeadLetterQueue() {
        EventProperties properties = new EventProperties();
        properties.getOutbox().setMaxAttempts(1);
        OutboxEvent event = event();
        when(repository.findByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                anyString(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        publisher(properties).publishBatch();

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(saved.getValue().getAttempts()).isEqualTo(1);
        assertThat(saved.getValue().getLastError()).contains("broker down");
        verify(deadLetterService).publish(anyString(), anyString(), anyString());
    }

    @Test
    void respectsConfiguredBatchSize() {
        EventProperties properties = new EventProperties();
        when(repository.findByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                anyString(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        new OutboxPublisher(repository, kafkaTemplate, deadLetterService, properties, 7).publishBatch();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                anyString(), any(LocalDateTime.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(7);
    }
}
