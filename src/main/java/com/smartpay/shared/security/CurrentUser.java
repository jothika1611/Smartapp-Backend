package com.smartpay.shared.security;

/**
 * Cross-module authentication contract. Controllers read this instead of importing identity
 * classes, so wallet/payment never depend on the identity module.
 */
public interface CurrentUser {

    Long getId();

    String getEmail();

    boolean isAdmin();
}
