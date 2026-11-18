package com.smartpay.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Thresholds that may be overridden with environment variables in application.yml. */
@ConfigurationProperties(prefix = "app.fraud")
public class FraudProperties {

    private int lowMax = 24;
    private int mediumMax = 49;
    private int highMin = 50;
    private int minDistinctRulesForHold = 2;
    private BigDecimal amountCap = new BigDecimal("50000.00");

    public int getLowMax() { return lowMax; }
    public void setLowMax(int lowMax) { this.lowMax = lowMax; }
    public int getMediumMax() { return mediumMax; }
    public void setMediumMax(int mediumMax) { this.mediumMax = mediumMax; }
    public int getHighMin() { return highMin; }
    public void setHighMin(int highMin) { this.highMin = highMin; }
    public int getMinDistinctRulesForHold() { return minDistinctRulesForHold; }
    public void setMinDistinctRulesForHold(int minDistinctRulesForHold) {
        this.minDistinctRulesForHold = minDistinctRulesForHold;
    }
    public BigDecimal getAmountCap() { return amountCap; }
    public void setAmountCap(BigDecimal amountCap) { this.amountCap = amountCap; }
}
