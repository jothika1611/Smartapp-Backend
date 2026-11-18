package com.smartpay.shared.identity;

import java.util.Optional;

/**
 * Small cross-module contract. Payment needs to check that a receiver exists but must not import
 * identity entities or repositories; the identity module supplies this implementation.
 */
public interface UserDirectory {

    boolean userExists(Long userId);

    /** Case-insensitive lookup used to resolve a receiver email from a payment request. */
    Optional<Long> findUserIdByEmail(String email);

    /** Small display projection for admin screens; never exposes credentials. */
    Optional<UserSummary> findSummary(Long userId);
}
