package com.smartpay.payment.controller;

import com.smartpay.shared.security.CurrentUser;
import com.smartpay.payment.dto.DemoFundingRequest;
import com.smartpay.payment.dto.FundingResponse;
import com.smartpay.payment.service.DemoFundingService;
import com.smartpay.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - simulated funding",
        description = "ADMIN-only creation of clearly-labelled simulated demo money")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminFundingController {

    private final DemoFundingService demoFundingService;

    public AdminFundingController(DemoFundingService demoFundingService) {
        this.demoFundingService = demoFundingService;
    }

    @Operation(summary = "Credit simulated money to a customer wallet (no real money involved)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/demo-funding")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FundingResponse> fund(
            @AuthenticationPrincipal CurrentUser admin,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DemoFundingRequest request) {
        return ApiResponse.ok("Simulated funding recorded. No real money is involved.",
                demoFundingService.fund(admin.getId(), idempotencyKey, request));
    }
}
