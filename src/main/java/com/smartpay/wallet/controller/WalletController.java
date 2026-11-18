package com.smartpay.wallet.controller;

import com.smartpay.shared.security.CurrentUser;
import com.smartpay.shared.dto.ApiResponse;
import com.smartpay.shared.dto.PageResponse;
import com.smartpay.wallet.dto.LedgerEntryResponse;
import com.smartpay.wallet.dto.WalletResponse;
import com.smartpay.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wallet", description = "Simulated wallet balance and immutable ledger")
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Operation(summary = "Get the authenticated user's simulated wallet")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ApiResponse<WalletResponse> myWallet(@AuthenticationPrincipal CurrentUser principal) {
        return ApiResponse.ok(walletService.getWallet(principal.getId()));
    }

    @Operation(summary = "Paginated immutable ledger for the authenticated user's wallet")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me/ledger")
    public ApiResponse<PageResponse<LedgerEntryResponse>> myLedger(
            @AuthenticationPrincipal CurrentUser principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(walletService.getLedger(principal.getId(), pageable));
    }
}
