package com.smartpay.fraud.velocity;

import com.smartpay.payment.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Redis-backed cache for velocity counts. MySQL calculates the value on a miss; Redis only stores it
 * briefly. Any Redis failure falls straight back to MySQL, so fraud decisions never depend on Redis.
 */
@Service("redisVelocityCounterService")
@Primary
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisVelocityCounterService implements VelocityCounterService {

    private static final Logger log = LoggerFactory.getLogger(RedisVelocityCounterService.class);

    private final StringRedisTemplate redisTemplate;
    private final TransactionRepository transactionRepository;
    private final long ttlSeconds;

    public RedisVelocityCounterService(StringRedisTemplate redisTemplate,
                                       TransactionRepository transactionRepository,
                                       @Value("${app.redis.velocity-ttl-seconds:30}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.transactionRepository = transactionRepository;
        this.ttlSeconds = Math.max(1L, ttlSeconds);
    }

    @Override
    public long countRecent(Long senderUserId, int windowMinutes) {
        String key = "smartpay:velocity:" + senderUserId + ":" + windowMinutes;
        try {
            ValueOperations<String, String> values = redisTemplate.opsForValue();
            String cached = values.get(key);
            if (cached != null) {
                return Long.parseLong(cached);
            }
            long count = fromMysql(senderUserId, windowMinutes);
            values.set(key, Long.toString(count), Duration.ofSeconds(ttlSeconds));
            return count;
        } catch (RuntimeException exception) {
            log.warn("Redis velocity cache unavailable; using MySQL: {}", exception.getMessage());
            return fromMysql(senderUserId, windowMinutes);
        }
    }

    private long fromMysql(Long senderUserId, int windowMinutes) {
        return transactionRepository.countBySenderUserIdAndCreatedAtAfter(
                senderUserId, LocalDateTime.now().minusMinutes(windowMinutes));
    }
}
