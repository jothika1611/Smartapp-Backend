package com.smartpay.shared.fraud;

import java.util.List;

/**
 * Deterministic rule result. The AI layer may only rewrite {@link #explanation()} later; it can
 * never change the score, level or hits.
 */
public record FraudDecision(int score,
                            String riskLevel,
                            String explanation,
                            List<RuleHit> hits) {

    /** Convenience constructor for Phase 5 tests and simple LOW defaults. */
    public FraudDecision(int score, String riskLevel) {
        this(score, riskLevel, "", List.of());
    }

    /** One fired rule with its own evidence and points. */
    public record RuleHit(String ruleCode, int points, String detail) {
    }
}
