package com.smartpay.event.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDedupeServiceTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    @Test
    void onlyFirstEventIdIsAccepted() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1, 0);

        EventDedupeService service = new EventDedupeService(entityManager);

        assertThat(service.markProcessed("event-1", "smartpay-notification")).isTrue();
        assertThat(service.markProcessed("event-1", "smartpay-notification")).isFalse();
    }
}
