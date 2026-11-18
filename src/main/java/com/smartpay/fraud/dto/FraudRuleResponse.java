package com.smartpay.fraud.dto;

import com.smartpay.fraud.entity.FraudRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Safe rule view. No JPA entity crosses the controller boundary. */
public class FraudRuleResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final String description;
    private final String category;
    private final int maxPoints;
    private final BigDecimal thresholdValue;
    private final Integer timeWindowMinutes;
    private final boolean enabled;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public FraudRuleResponse(Long id, String code, String name, String description, String category,
                             int maxPoints, BigDecimal thresholdValue, Integer timeWindowMinutes,
                             boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.category = category;
        this.maxPoints = maxPoints;
        this.thresholdValue = thresholdValue;
        this.timeWindowMinutes = timeWindowMinutes;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FraudRuleResponse from(FraudRule rule) {
        return new FraudRuleResponse(rule.getId(), rule.getCode(), rule.getName(),
                rule.getDescription(), rule.getCategory(), rule.getMaxPoints(),
                rule.getThresholdValue(), rule.getTimeWindowMinutes(), rule.isEnabled(),
                rule.getCreatedAt(), rule.getUpdatedAt());
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public int getMaxPoints() { return maxPoints; }
    public BigDecimal getThresholdValue() { return thresholdValue; }
    public Integer getTimeWindowMinutes() { return timeWindowMinutes; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
