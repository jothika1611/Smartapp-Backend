package com.smartpay.wallet.mapper;

import com.smartpay.wallet.dto.WalletResponse;
import com.smartpay.wallet.entity.Wallet;

/** Converts the wallet entity into a client-safe DTO. */
public final class WalletMapper {

    private WalletMapper() {
    }

    public static WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getCurrency(),
                wallet.getAvailableBalance(),
                wallet.getLedgerBalance(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }
}
