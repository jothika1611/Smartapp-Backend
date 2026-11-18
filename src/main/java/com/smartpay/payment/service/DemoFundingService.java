package com.smartpay.payment.service;

import com.smartpay.payment.dto.DemoFundingRequest;
import com.smartpay.payment.dto.FundingResponse;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.RiskLevel;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.entity.enums.TransactionType;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.shared.identity.UserDirectory;
import com.smartpay.shared.money.MoneySupport;
import com.smartpay.wallet.dto.WalletResponse;
import com.smartpay.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Simulated money creation for demos. It is ADMIN-only, records a FUNDING transaction and credits
 * the customer's wallet in one database transaction. The idempotency key prevents double funding
 * when an ADMIN retries the same request.
 */
@Service
public class DemoFundingService {

    private static final String DEFAULT_DESCRIPTION = "Simulated demo funding by ADMIN";

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final UserDirectory userDirectory;

    public DemoFundingService(TransactionRepository transactionRepository,
                              WalletService walletService,
                              UserDirectory userDirectory) {
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.userDirectory = userDirectory;
    }

    @Transactional
    public FundingResponse fund(Long adminUserId, String idempotencyKey, DemoFundingRequest request) {
        String key = requireKey(idempotencyKey);
        BigDecimal amount = MoneySupport.requirePositive(request.getAmount());

        Optional<Transaction> existing =
                transactionRepository.findBySenderUserIdAndIdempotencyKey(adminUserId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), request, amount);
        }

        if (!userDirectory.userExists(request.getUserId())) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, "Receiver user not found");
        }

        Transaction transaction = new Transaction();
        transaction.setReference(nextReference());
        transaction.setSenderUserId(adminUserId);
        transaction.setReceiverUserId(request.getUserId());
        transaction.setType(TransactionType.FUNDING);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setAmount(amount);
        transaction.setCurrency(MoneySupport.DEFAULT_CURRENCY);
        transaction.setIdempotencyKey(key);
        transaction.setDescription(descriptionOrDefault(request.getDescription()));
        transaction.setRiskScore(0);
        transaction.setRiskLevel(RiskLevel.LOW);
        transaction.setProcessedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(transaction);

        WalletResponse wallet = walletService.credit(
                saved.getReceiverUserId(), amount, saved.getId(), saved.getDescription());

        return toResponse(saved, wallet);
    }

    private FundingResponse replay(Transaction existing, DemoFundingRequest request, BigDecimal amount) {
        boolean sameReceiver = existing.getReceiverUserId().equals(request.getUserId());
        boolean sameAmount = existing.getAmount().compareTo(amount) == 0;
        if (!sameReceiver || !sameAmount) {
            throw new ApiException(ErrorCode.DUPLICATE_REQUEST,
                    "Idempotency key was already used with a different payload");
        }
        WalletResponse wallet = walletService.getWallet(existing.getReceiverUserId());
        return toResponse(existing, wallet);
    }

    private FundingResponse toResponse(Transaction transaction, WalletResponse wallet) {
        return new FundingResponse(
                transaction.getReference(),
                transaction.getReceiverUserId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus().name(),
                wallet.getAvailableBalance()
        );
    }

    private String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 100) {
            throw new ApiException(ErrorCode.INVALID_IDEMPOTENCY_KEY,
                    "Idempotency key must be at most 100 characters");
        }
        return trimmed;
    }

    private String descriptionOrDefault(String description) {
        return description == null || description.isBlank()
                ? DEFAULT_DESCRIPTION
                : description.trim();
    }

    private String nextReference() {
        String random = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        return "FND-" + random;
    }
}
