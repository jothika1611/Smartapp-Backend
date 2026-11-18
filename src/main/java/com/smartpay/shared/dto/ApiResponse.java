package com.smartpay.shared.dto;

import java.time.Instant;

/** Standard success wrapper. The simulated flag is always true in this demo application. */
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final boolean simulated = true;
    private final Instant timestamp = Instant.now();

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
