package com.smartpay.fraud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One deterministic assessment per transaction. AI text is added later without changing the score. */
@Entity
@Table(name = "fraud_assessments")
public class FraudAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;

    @Column(nullable = false)
    private int score;

    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    @Column(name = "explanation_deterministic", nullable = false, columnDefinition = "TEXT")
    private String explanationDeterministic;

    @Column(name = "explanation_ai", columnDefinition = "TEXT")
    private String explanationAi;

    @Column(name = "ai_source", nullable = false, length = 20)
    private String aiSource = "MOCK";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getExplanationDeterministic() { return explanationDeterministic; }
    public void setExplanationDeterministic(String explanationDeterministic) {
        this.explanationDeterministic = explanationDeterministic;
    }
    public String getExplanationAi() { return explanationAi; }
    public void setExplanationAi(String explanationAi) { this.explanationAi = explanationAi; }
    public String getAiSource() { return aiSource; }
    public void setAiSource(String aiSource) { this.aiSource = aiSource; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
