package com.smartpay.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One immutable ledger line, exposed for the customer's wallet statement. */
public class LedgerEntryResponse {

    private final Long id;
    private final String entryType;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final String description;
    private final Long transactionId;
    private final LocalDateTime createdAt;

    public LedgerEntryResponse(Long id, String entryType, BigDecimal amount, BigDecimal balanceAfter,
                               String description, Long transactionId, LocalDateTime createdAt) {
        this.id = id;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public Long getTransactionId() { return transactionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
