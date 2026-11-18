package com.smartpay.wallet.mapper;

import com.smartpay.wallet.dto.LedgerEntryResponse;
import com.smartpay.wallet.entity.WalletLedgerEntry;

/** Converts an immutable ledger row into a client-safe DTO. */
public final class LedgerEntryMapper {

    private LedgerEntryMapper() {
    }

    public static LedgerEntryResponse toResponse(WalletLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getDescription(),
                entry.getTransactionId(),
                entry.getCreatedAt()
        );
    }
}
