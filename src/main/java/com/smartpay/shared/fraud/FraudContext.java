package com.smartpay.shared.fraud;

import java.math.BigDecimal;

/** Input for a risk screen. Contains no JPA entities so any module can build it. */
public record FraudContext(Long senderUserId,
                           Long receiverUserId,
                           BigDecimal amount,
                           String currency,
                           String transactionType,
                           String deviceFingerprint,
                           String locationHint,
                           String description) {
}
