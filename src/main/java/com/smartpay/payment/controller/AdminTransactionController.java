package com.smartpay.payment.controller;

import com.smartpay.fraud.dto.AdminInvestigationResponse;
import com.smartpay.fraud.service.FraudInvestigationService;
import com.smartpay.payment.dto.TransactionResponse;
import com.smartpay.payment.service.AdminReviewService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** ADMIN-only transaction review. Route authorization is enforced by SecurityConfig. */
@Tag(name = "Admin transaction review", description = "Search, investigate, approve and reject simulated transactions")
@RestController
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {

    private final TransactionQueryService queryService;
    private final AdminReviewService reviewService;
    private final FraudInvestigationService investigationService;

    public AdminTransactionController(TransactionQueryService queryService,
                                      AdminReviewService reviewService,
                                      FraudInvestigationService investigationService) {
        this.queryService = queryService;
        this.reviewService = reviewService;
        this.investigationService = investigationService;
    }

    @Operation(summary = "Search all simulated transactions with the same filters as history")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> search(
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
        return ApiResponse.ok(queryService.search(user.getId(), true, page, size,
                status, type, from, to, query));
    }

    @Operation(summary = "Full investigation view with deterministic risk evidence")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{reference}/investigation")
    public ApiResponse<AdminInvestigationResponse> investigation(@PathVariable String reference) {
        return ApiResponse.ok(investigationService.get(reference));
    }

    @Operation(summary = "Approve a PENDING or HOLD transaction and release the simulated money")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{reference}/approve")
    public ApiResponse<TransactionResponse> approve(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String reference) {
        return ApiResponse.ok("Simulated transaction approved", reviewService.approve(user.getId(), reference));
    }

    @Operation(summary = "Reject a PENDING or HOLD transaction without moving money")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{reference}/reject")
    public ApiResponse<TransactionResponse> reject(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String reference) {
        return ApiResponse.ok("Simulated transaction rejected", reviewService.reject(user.getId(), reference));
    }
}
