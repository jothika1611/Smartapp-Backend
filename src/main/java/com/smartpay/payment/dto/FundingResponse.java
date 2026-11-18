package com.smartpay.payment.dto;

import java.math.BigDecimal;

/** Result of a simulated funding operation, including the wallet balance afterwards. */
public class FundingResponse {

    private final String reference;
    private final Long receiverUserId;
    private final BigDecimal amount;
    private final String currency;
    private final String status;
    private final BigDecimal availableBalance;

    public FundingResponse(String reference, Long receiverUserId, BigDecimal amount, String currency,
                           String status, BigDecimal availableBalance) {
        this.reference = reference;
        this.receiverUserId = receiverUserId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.availableBalance = availableBalance;
    }

    public String getReference() { return reference; }
    public Long getReceiverUserId() { return receiverUserId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
}
