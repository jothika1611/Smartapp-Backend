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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers", description = "Simulated customer-to-customer transfers")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final PaymentService paymentService;

    public TransferController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Create a simulated transfer to another registered user")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionResponse> create(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok("Simulated transfer created. No real money is involved.",
                paymentService.initiate(user.getId(), idempotencyKey, request, TransactionType.TRANSFER));
    }
}
