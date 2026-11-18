package com.smartpay.wallet.service;

import com.smartpay.shared.dto.PageResponse;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.shared.money.MoneySupport;
import com.smartpay.wallet.dto.LedgerEntryResponse;
import com.smartpay.wallet.dto.WalletResponse;
import com.smartpay.wallet.dto.WalletTransferResult;
import com.smartpay.wallet.entity.LedgerEntryType;
import com.smartpay.wallet.entity.Wallet;
import com.smartpay.wallet.entity.WalletLedgerEntry;
import com.smartpay.wallet.mapper.LedgerEntryMapper;
import com.smartpay.wallet.mapper.WalletMapper;
import com.smartpay.wallet.repository.WalletLedgerEntryRepository;
import com.smartpay.wallet.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The only writer of wallet balances. Every change locks the wallet row, updates both balance
 * columns and appends an immutable ledger entry in the same transaction.
 */
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletLedgerEntryRepository ledgerRepository;

    public WalletService(WalletRepository walletRepository,
                         WalletLedgerEntryRepository ledgerRepository) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public WalletResponse getWallet(Long userId) {
        return WalletMapper.toResponse(loadOrCreate(userId));
    }

    /** Idempotent: safe to call from registration, funding or the first wallet read. */
    @Transactional
    public void ensureWallet(Long userId) {
        loadOrCreate(userId);
    }

    /** Adds simulated money and returns the new wallet view. */
    @Transactional
    public WalletResponse credit(Long userId, BigDecimal amount, Long transactionId, String description) {
        BigDecimal value = MoneySupport.requirePositive(amount);

        Wallet wallet = lockOrCreate(userId);
        BigDecimal balanceAfter = wallet.getAvailableBalance().add(value);
        applyBalance(wallet, balanceAfter);
        writeLedger(wallet, LedgerEntryType.CREDIT, value, balanceAfter, transactionId, description);
        return WalletMapper.toResponse(wallet);
    }

    /** Removes simulated money only when sufficient funds exist. Phase 5 uses this for transfers. */
    @Transactional
    public WalletResponse debit(Long userId, BigDecimal amount, Long transactionId, String description) {
        BigDecimal value = MoneySupport.requirePositive(amount);

        Wallet wallet = lockOrCreate(userId);
        if (wallet.getAvailableBalance().compareTo(value) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS);
        }

        BigDecimal balanceAfter = wallet.getAvailableBalance().subtract(value);
        applyBalance(wallet, balanceAfter);
        writeLedger(wallet, LedgerEntryType.DEBIT, value, balanceAfter, transactionId, description);
        return WalletMapper.toResponse(wallet);
    }

    /**
     * Moves simulated money between two wallets in one transaction. The rows are locked in
     * ascending user-id order, so two simultaneous transfers in opposite directions cannot deadlock.
     */
    @Transactional
    public WalletTransferResult transfer(Long senderUserId, Long receiverUserId, BigDecimal amount,
                                         Long transactionId, String senderDescription,
                                         String receiverDescription) {
        BigDecimal value = MoneySupport.requirePositive(amount);
        requireUserId(senderUserId);
        requireUserId(receiverUserId);
        if (senderUserId.equals(receiverUserId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Sender and receiver must be different");
        }

        Long firstUserId = Math.min(senderUserId, receiverUserId);
        Long secondUserId = Math.max(senderUserId, receiverUserId);
        Wallet first = lockOrCreate(firstUserId);
        Wallet second = lockOrCreate(secondUserId);
        Wallet sender = senderUserId.equals(firstUserId) ? first : second;
        Wallet receiver = receiverUserId.equals(firstUserId) ? first : second;

        if (sender.getAvailableBalance().compareTo(value) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS);
        }

        BigDecimal senderAfter = sender.getAvailableBalance().subtract(value);
        BigDecimal receiverAfter = receiver.getAvailableBalance().add(value);
        applyBalance(sender, senderAfter);
        applyBalance(receiver, receiverAfter);
        writeLedger(sender, LedgerEntryType.DEBIT, value, senderAfter, transactionId, senderDescription);
        writeLedger(receiver, LedgerEntryType.CREDIT, value, receiverAfter, transactionId, receiverDescription);

        return new WalletTransferResult(
                WalletMapper.toResponse(sender), WalletMapper.toResponse(receiver));
    }

    @Transactional
    public PageResponse<LedgerEntryResponse> getLedger(Long userId, Pageable pageable) {
        Wallet wallet = loadOrCreate(userId);
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<WalletLedgerEntry> page =
                ledgerRepository.findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), safePageable);
        return PageResponse.from(page.map(LedgerEntryMapper::toResponse));
    }

    private Wallet loadOrCreate(Long userId) {
        requireUserId(userId);
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(newWallet(userId)));
    }

    /**
     * Locks the wallet row before reading its version. Loading the entity first would keep a stale
     * @Version snapshot in the persistence context, so queued concurrent updates would fail with an
     * optimistic-lock exception even though the pessimistic lock serialised them.
     */
    private Wallet lockOrCreate(Long userId) {
        requireUserId(userId);
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.save(newWallet(userId)));
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private Wallet newWallet(Long userId) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setCurrency(MoneySupport.DEFAULT_CURRENCY);
        wallet.setAvailableBalance(MoneySupport.zero());
        wallet.setLedgerBalance(MoneySupport.zero());
        return wallet;
    }

    private void applyBalance(Wallet wallet, BigDecimal balanceAfter) {
        wallet.setAvailableBalance(balanceAfter);
        wallet.setLedgerBalance(balanceAfter);
        walletRepository.save(wallet);
    }

    private void writeLedger(Wallet wallet, LedgerEntryType type, BigDecimal amount,
                             BigDecimal balanceAfter, Long transactionId, String description) {
        WalletLedgerEntry entry = new WalletLedgerEntry();
        entry.setWalletId(wallet.getId());
        entry.setTransactionId(transactionId);
        entry.setEntryType(type);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        entry.setDescription(description);
        ledgerRepository.save(entry);
    }
}
