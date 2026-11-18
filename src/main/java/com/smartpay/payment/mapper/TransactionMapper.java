package com.smartpay.payment.mapper;

import com.smartpay.payment.dto.TransactionResponse;
import com.smartpay.payment.entity.Transaction;

/** Converts the transaction entity into a client-safe DTO. */
public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getSenderUserId(),
                transaction.getReceiverUserId(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getRiskScore(),
                transaction.getRiskLevel().name(),
                transaction.getDeviceFingerprint(),
                transaction.getLocationHint(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getProcessedAt()
        );
    }
}
