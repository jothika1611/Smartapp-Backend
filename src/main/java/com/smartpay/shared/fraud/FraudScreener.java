package com.smartpay.shared.fraud;

/**
 * Phase 5 ships a default LOW implementation. Phase 6 supplies the real rule engine through this
 * interface, so payment code does not change when scoring arrives.
 */
public interface FraudScreener {

    FraudDecision assess(FraudContext context);
}
