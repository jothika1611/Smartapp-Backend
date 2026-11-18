package com.smartpay.shared.dto;

import java.util.ArrayList;
import java.util.List;

/** Standard error wrapper. Never contains stack traces, SQL, password hashes or secrets. */
public class ErrorResponse {

    private final boolean success = false;
    private final int status;
    private final String message;
    private final List<FieldError> errors = new ArrayList<>();
    private final String traceId;
    private final boolean simulated = true;

    public ErrorResponse(int status, String message, String traceId) {
        this.status = status;
        this.message = message;
        this.traceId = traceId;
    }

    public void addFieldError(String field, String message) {
        errors.add(new FieldError(field, message));
    }

    public boolean isSuccess() { return success; }
    public int getStatus() { return status; }
    public String getMessage() { return message; }
    public List<FieldError> getErrors() { return errors; }
    public String getTraceId() { return traceId; }
    public boolean isSimulated() { return simulated; }

    /** One invalid request field. */
    public static class FieldError {
        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
    }
}
