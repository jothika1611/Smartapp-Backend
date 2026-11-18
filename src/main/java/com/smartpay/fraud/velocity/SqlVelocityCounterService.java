package com.smartpay.fraud.velocity;

import com.smartpay.payment.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Default counter: always read MySQL, the authoritative source. */
@Service("sqlVelocityCounterService")
public class SqlVelocityCounterService implements VelocityCounterService {

    private final TransactionRepository transactionRepository;

    public SqlVelocityCounterService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public long countRecent(Long senderUserId, int windowMinutes) {
        return transactionRepository.countBySenderUserIdAndCreatedAtAfter(
                senderUserId, LocalDateTime.now().minusMinutes(windowMinutes));
    }
}
