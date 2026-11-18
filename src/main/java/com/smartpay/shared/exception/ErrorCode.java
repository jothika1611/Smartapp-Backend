package com.smartpay.shared.exception;

/** Stable business error codes used in JSON error responses. */
public enum ErrorCode {

    VALIDATION_FAILED(400, "Validation failed"),
    EMAIL_ALREADY_EXISTS(409, "Email already registered"),
    ROLE_NOT_FOUND(500, "Required role is not configured"),
    USER_NOT_FOUND(404, "User not found"),
    UNAUTHORIZED(401, "Authentication required"),
    FORBIDDEN(403, "Access denied"),
    INSUFFICIENT_FUNDS(422, "Insufficient simulated balance"),
    INVALID_IDEMPOTENCY_KEY(409, "Idempotency key is required"),
    DUPLICATE_REQUEST(409, "Duplicate request with a different payload"),
    TRANSACTION_NOT_FOUND(404, "Transaction not found"),
    ALERT_NOT_FOUND(404, "Fraud alert not found"),
    RULE_NOT_FOUND(404, "Fraud rule not found"),
    RULE_CODE_EXISTS(409, "Fraud rule code already exists"),
    INVALID_STATE(422, "Operation is not allowed in the current state"),
    INTERNAL_ERROR(500, "Unexpected error");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
