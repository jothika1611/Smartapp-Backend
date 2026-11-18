package com.smartpay.fraud.velocity;

/** Supplies a short-window transaction count for the VELOCITY fraud rule. */
public interface VelocityCounterService {

    /** Counts transactions for a sender during the requested window. */
    long countRecent(Long senderUserId, int windowMinutes);
}
