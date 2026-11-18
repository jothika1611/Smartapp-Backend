package com.smartpay.wallet.repository;

import com.smartpay.wallet.entity.WalletLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletLedgerEntryRepository extends JpaRepository<WalletLedgerEntry, Long> {

    Page<WalletLedgerEntry> findByWalletIdOrderByCreatedAtDescIdDesc(Long walletId, Pageable pageable);

    long countByWalletId(Long walletId);
}
