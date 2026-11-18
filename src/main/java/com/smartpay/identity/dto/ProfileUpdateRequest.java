package com.smartpay.identity.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProfileUpdateRequest {

    @Size(max = 120, message = "fullName must not exceed 120 characters")
    private String fullName;

    @Pattern(regexp = "^[0-9]{7,15}$", message = "phone must contain 7 to 15 digits")
    private String phone;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
