package com.smartpay.shared.identity;

/**
 * Minimal, non-sensitive identity projection shared with other modules.
 * Never includes password hashes, roles or tokens.
 */
public record UserSummary(Long id, String fullName, String email) {
}
