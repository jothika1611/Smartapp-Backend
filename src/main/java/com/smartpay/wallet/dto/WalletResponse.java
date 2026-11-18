package com.smartpay.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Safe wallet view. Contains no entity references and no secrets. */
public class WalletResponse {

    private final Long id;
    private final Long userId;
    private final String currency;
    private final BigDecimal availableBalance;
    private final BigDecimal ledgerBalance;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public WalletResponse(Long id, Long userId, String currency, BigDecimal availableBalance,
                          BigDecimal ledgerBalance, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.currency = currency;
        this.availableBalance = availableBalance;
        this.ledgerBalance = ledgerBalance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCurrency() { return currency; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public BigDecimal getLedgerBalance() { return ledgerBalance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
