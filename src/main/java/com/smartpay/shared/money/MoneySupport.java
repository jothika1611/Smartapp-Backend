package com.smartpay.shared.money;

import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Central money rules. Never use double or float for money. */
public final class MoneySupport {

    public static final String DEFAULT_CURRENCY = "INR";
    public static final int MAX_INTEGER_DIGITS = 17;

    private MoneySupport() {
    }

    /**
     * Validates a positive money amount and returns it with exactly two decimal places.
     * DECIMAL(19,2) allows 17 integer digits plus 2 decimal digits.
     */
    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must be greater than zero");
        }

        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() > 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Amount must have at most 2 decimal places");
        }
        if (normalized.precision() - normalized.scale() > MAX_INTEGER_DIGITS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount is too large");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }
}
