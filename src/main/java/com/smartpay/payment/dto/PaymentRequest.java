package com.smartpay.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request body shared by simulated payments and transfers. */
public class PaymentRequest {

    @NotBlank(message = "receiverEmail is required")
    @Email(message = "receiverEmail must be a valid email")
    @Size(max = 120, message = "receiverEmail must not exceed 120 characters")
    private String receiverEmail;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
    @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
    private BigDecimal amount;

    @Size(min = 3, max = 3, message = "currency must be a 3-letter code")
    private String currency;

    @Size(max = 255, message = "description must not exceed 255 characters")
    private String description;

    @Size(max = 100, message = "deviceFingerprint must not exceed 100 characters")
    private String deviceFingerprint;

    @Size(max = 100, message = "locationHint must not exceed 100 characters")
    private String locationHint;

    public String getReceiverEmail() { return receiverEmail; }
    public void setReceiverEmail(String receiverEmail) { this.receiverEmail = receiverEmail; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    public String getLocationHint() { return locationHint; }
    public void setLocationHint(String locationHint) { this.locationHint = locationHint; }
}
