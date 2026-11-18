package com.smartpay.payment.controller;

import com.smartpay.payment.dto.PaymentRequest;
import com.smartpay.payment.dto.TransactionResponse;
import com.smartpay.payment.entity.enums.TransactionType;
import com.smartpay.payment.service.PaymentService;
import com.smartpay.shared.dto.ApiResponse;
import com.smartpay.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Simulated payments with risk screening and idempotency")
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Create a simulated payment")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok("Simulated payment created. No real money is involved.",
                paymentService.initiate(user.getId(), idempotencyKey, request, TransactionType.PAYMENT));
    }

    @Operation(summary = "Get payment/transfer status by reference")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{reference}/status")
    public ApiResponse<TransactionResponse> status(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String reference) {
        return ApiResponse.ok(paymentService.status(user.getId(), user.isAdmin(), reference));
    }

    @Operation(summary = "Cancel a PENDING simulated payment (sender only)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{reference}/cancel")
    public ApiResponse<TransactionResponse> cancel(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String reference) {
        return ApiResponse.ok("Simulated payment cancelled. No money was moved.",
                paymentService.cancel(user.getId(), reference));
    }
}
