package com.smartpay.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Safe transaction view for status, history and detail endpoints. */
public class TransactionResponse {

    private final Long id;
    private final String reference;
    private final Long senderUserId;
    private final Long receiverUserId;
    private final String type;
    private final String status;
    private final BigDecimal amount;
    private final String currency;
    private final int riskScore;
    private final String riskLevel;
    private final String deviceFingerprint;
    private final String locationHint;
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime processedAt;

    public TransactionResponse(Long id, String reference, Long senderUserId, Long receiverUserId,
                               String type, String status, BigDecimal amount, String currency,
                               int riskScore, String riskLevel, String deviceFingerprint,
                               String locationHint, String description, LocalDateTime createdAt,
                               LocalDateTime processedAt) {
        this.id = id;
        this.reference = reference;
        this.senderUserId = senderUserId;
        this.receiverUserId = receiverUserId;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.deviceFingerprint = deviceFingerprint;
        this.locationHint = locationHint;
        this.description = description;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Long getSenderUserId() { return senderUserId; }
    public Long getReceiverUserId() { return receiverUserId; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public int getRiskScore() { return riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public String getLocationHint() { return locationHint; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
