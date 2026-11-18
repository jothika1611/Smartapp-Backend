package com.smartpay.fraud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ADMIN notes required when closing an alert. */
public class ResolveAlertRequest {

    @NotBlank
    @Size(max = 2000)
    private String resolutionNotes;

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
}
