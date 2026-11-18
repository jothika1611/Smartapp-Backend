package com.smartpay.payment.controller;

import com.smartpay.payment.dto.TransactionResponse;
import com.smartpay.payment.service.PaymentService;
import com.smartpay.payment.service.TransactionQueryService;
import com.smartpay.shared.dto.ApiResponse;
import com.smartpay.shared.dto.PageResponse;
import com.smartpay.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "Transactions", description = "Filtered transaction history and detail")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionQueryService queryService;
    private final PaymentService paymentService;

    public TransactionController(TransactionQueryService queryService, PaymentService paymentService) {
        this.queryService = queryService;
        this.paymentService = paymentService;
    }

    @Operation(summary = "Paginated history; customers see only their own transactions, admins see all")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> history(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String query) {
        return ApiResponse.ok(queryService.search(user.getId(), user.isAdmin(), page, size,
                status, type, from, to, query));
    }

    @Operation(summary = "Transaction detail by server-generated reference")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{reference}")
    public ApiResponse<TransactionResponse> detail(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String reference) {
        return ApiResponse.ok(paymentService.detail(user.getId(), user.isAdmin(), reference));
    }
}
