package com.smartpay.fraud.velocity;

import com.smartpay.payment.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisVelocityCounterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private TransactionRepository transactionRepository;

    private RedisVelocityCounterService service() {
        return new RedisVelocityCounterService(redisTemplate, transactionRepository, 30L);
    }

    @Test
    void returnsCachedCountWithoutTouchingMysql() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("smartpay:velocity:1:5")).thenReturn("7");

        assertThat(service().countRecent(1L, 5)).isEqualTo(7L);

        verify(transactionRepository, never())
                .countBySenderUserIdAndCreatedAtAfter(anyLong(), any(LocalDateTime.class));
    }

    @Test
    void cacheMissReadsMysqlAndStoresShortLivedValue() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("smartpay:velocity:2:10")).thenReturn(null);
        when(transactionRepository.countBySenderUserIdAndCreatedAtAfter(
                anyLong(), any(LocalDateTime.class))).thenReturn(4L);

        assertThat(service().countRecent(2L, 10)).isEqualTo(4L);

        verify(values).set("smartpay:velocity:2:10", "4", Duration.ofSeconds(30L));
    }

    @Test
    void redisFailureFallsBackToMysql() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("smartpay:velocity:3:5")).thenThrow(new RuntimeException("redis down"));
        when(transactionRepository.countBySenderUserIdAndCreatedAtAfter(
                anyLong(), any(LocalDateTime.class))).thenReturn(9L);

        assertThat(service().countRecent(3L, 5)).isEqualTo(9L);

        verify(transactionRepository).countBySenderUserIdAndCreatedAtAfter(
                anyLong(), any(LocalDateTime.class));
    }
}
