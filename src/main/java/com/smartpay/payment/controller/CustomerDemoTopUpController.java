package com.smartpay.payment.controller;

import com.smartpay.payment.dto.CustomerDemoTopUpRequest;
import com.smartpay.payment.dto.DemoFundingRequest;
import com.smartpay.payment.dto.FundingResponse;
import com.smartpay.payment.service.DemoFundingService;
import com.smartpay.shared.dto.ApiResponse;
import com.smartpay.shared.security.CurrentUser;
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

/**
 * Customer-controlled simulated top-up. There is no payment gateway, bank or card here;
 * the wallet is credited with fake money only, and every response is labelled simulated.
 */
@Tag(name = "Customer - simulated demo top-up",
        description = "Add fake money to your own wallet for a portfolio demo; no real payment is taken")
@RestController
@RequestMapping("/api/v1/wallets/me")
public class CustomerDemoTopUpController {

    private final DemoFundingService demoFundingService;

    public CustomerDemoTopUpController(DemoFundingService demoFundingService) {
        this.demoFundingService = demoFundingService;
    }

    @Operation(summary = "Credit simulated demo money to the authenticated customer's own wallet")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/demo-topup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FundingResponse> topUp(
            @AuthenticationPrincipal CurrentUser principal,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CustomerDemoTopUpRequest request) {

        DemoFundingRequest funding = new DemoFundingRequest();
        funding.setUserId(principal.getId());
        funding.setAmount(request.getAmount());
        funding.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? "Simulated demo top-up requested by customer"
                : request.getDescription().trim());

        return ApiResponse.ok("Simulated demo top-up recorded. No real money is involved.",
                demoFundingService.fund(principal.getId(), idempotencyKey, funding));
    }
}
