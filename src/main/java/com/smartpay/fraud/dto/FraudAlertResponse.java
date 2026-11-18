package com.smartpay.fraud.dto;

import java.time.LocalDateTime;

/** ADMIN view of an alert; assessment is populated only on the detail endpoint. */
public class FraudAlertResponse {

    private final Long id;
    private final Long transactionId;
    private final String transactionReference;
    private final String senderName;
    private final String senderEmail;
    private final String receiverName;
    private final String receiverEmail;
    private final String severity;
    private final String status;
    private final int riskScore;
    private final String reasonSummary;
    private final Long assignedAdminId;
    private final String resolutionNotes;
    private final LocalDateTime createdAt;
    private final LocalDateTime resolvedAt;
    private final FraudAssessmentResponse assessment;

    public FraudAlertResponse(Long id, Long transactionId, String transactionReference,
                              String senderName, String senderEmail,
                              String receiverName, String receiverEmail,
                              String severity, String status, int riskScore,
                              String reasonSummary, Long assignedAdminId, String resolutionNotes,
                              LocalDateTime createdAt, LocalDateTime resolvedAt,
                              FraudAssessmentResponse assessment) {
        this.id = id;
        this.transactionId = transactionId;
        this.transactionReference = transactionReference;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.receiverName = receiverName;
        this.receiverEmail = receiverEmail;
        this.severity = severity;
        this.status = status;
        this.riskScore = riskScore;
        this.reasonSummary = reasonSummary;
        this.assignedAdminId = assignedAdminId;
        this.resolutionNotes = resolutionNotes;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
        this.assessment = assessment;
    }

    public Long getId() { return id; }
    public Long getTransactionId() { return transactionId; }
    public String getTransactionReference() { return transactionReference; }
    public String getSenderName() { return senderName; }
    public String getSenderEmail() { return senderEmail; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverEmail() { return receiverEmail; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public int getRiskScore() { return riskScore; }
    public String getReasonSummary() { return reasonSummary; }
    public Long getAssignedAdminId() { return assignedAdminId; }
    public String getResolutionNotes() { return resolutionNotes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public FraudAssessmentResponse getAssessment() { return assessment; }
}
