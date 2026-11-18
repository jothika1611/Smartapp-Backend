package com.smartpay.shared.event;

/**
 * Published inside the registration transaction. The wallet module listens synchronously and
 * creates the wallet in the same transaction, so a user can never be committed without a wallet.
 */
public record UserRegisteredEvent(Long userId, String email) {
}
