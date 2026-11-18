package com.smartpay.payment.service;

import com.smartpay.fraud.service.FraudAssessmentService;
import com.smartpay.event.config.EventProperties;
import com.smartpay.event.service.OutboxService;
import com.smartpay.payment.dto.PaymentRequest;
import com.smartpay.payment.dto.TransactionResponse;
import com.smartpay.fraud.service.FraudAssessmentService;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.RiskLevel;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.entity.enums.TransactionType;
import com.smartpay.payment.mapper.TransactionMapper;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.shared.fraud.FraudContext;
import com.smartpay.shared.fraud.FraudDecision;
import com.smartpay.shared.fraud.FraudScreener;
import com.smartpay.shared.identity.UserDirectory;
import com.smartpay.shared.money.MoneySupport;
import com.smartpay.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates simulated payments and transfers. The transaction row is written first so the ledger can
 * reference it; a failure at any later step rolls the whole operation back.
 */
@Service
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final UserDirectory userDirectory;
    private final WalletService walletService;
    private final FraudScreener fraudScreener;
    private final FraudAssessmentService fraudAssessmentService;
    private final OutboxService outboxService;
    private final EventProperties eventProperties;

    public PaymentService(TransactionRepository transactionRepository,
                          UserDirectory userDirectory,
                          WalletService walletService,
                          FraudScreener fraudScreener,
                          FraudAssessmentService fraudAssessmentService,
                          OutboxService outboxService,
                          EventProperties eventProperties) {
        this.transactionRepository = transactionRepository;
        this.userDirectory = userDirectory;
        this.walletService = walletService;
        this.fraudScreener = fraudScreener;
        this.fraudAssessmentService = fraudAssessmentService;
        this.outboxService = outboxService;
        this.eventProperties = eventProperties;
    }

    @Transactional
    public TransactionResponse initiate(Long senderUserId, String idempotencyKey,
                                        PaymentRequest request, TransactionType type) {
        String key = requireKey(idempotencyKey);
        BigDecimal amount = MoneySupport.requirePositive(request.getAmount());
        String currency = normalizeCurrency(request.getCurrency());

        Long receiverUserId = userDirectory.findUserIdByEmail(request.getReceiverEmail())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "Receiver user not found"));
        if (senderUserId.equals(receiverUserId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Sender and receiver must be different");
        }

        Optional<Transaction> existing =
                transactionRepository.findBySenderUserIdAndIdempotencyKey(senderUserId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), receiverUserId, amount, currency, type);
        }

        FraudDecision decision = fraudScreener.assess(new FraudContext(
                senderUserId, receiverUserId, amount, currency, type.name(),
                request.getDeviceFingerprint(), request.getLocationHint(), request.getDescription()));
        RiskLevel riskLevel = parseRiskLevel(decision.riskLevel());
        int riskScore = Math.max(0, Math.min(100, decision.score()));

        Transaction transaction = new Transaction();
        transaction.setReference(nextReference());
        transaction.setSenderUserId(senderUserId);
        transaction.setReceiverUserId(receiverUserId);
        transaction.setType(type);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setIdempotencyKey(key);
        transaction.setDeviceFingerprint(request.getDeviceFingerprint());
        transaction.setLocationHint(request.getLocationHint());
        transaction.setDescription(descriptionOrDefault(request.getDescription(), type));
        transaction.setRiskScore(riskScore);
        transaction.setRiskLevel(riskLevel);
        Transaction saved = transactionRepository.save(transaction);
        fraudAssessmentService.persist(saved.getId(), decision);
        outboxService.write(
                "TRANSACTION", saved.getReference(), "TRANSACTION_CREATED",
                eventProperties.getTopics().getTxCreated(), "tx-" + saved.getId(),
                transactionEventPayload(saved));

        switch (riskLevel) {
            case LOW -> {
                String typeLabel = type.name().toLowerCase(Locale.ROOT);
                walletService.transfer(senderUserId, receiverUserId, amount, saved.getId(),
                        "Simulated " + typeLabel + " sent",
                        "Simulated " + typeLabel + " received");
                saved.setStatus(TransactionStatus.SUCCESS);
                saved.setProcessedAt(LocalDateTime.now());
            }
            case MEDIUM -> saved.setStatus(TransactionStatus.PENDING);
            case HIGH -> saved.setStatus(TransactionStatus.HOLD);
        }
        transactionRepository.save(saved);
        return TransactionMapper.toResponse(saved);
    }

    @Transactional
    public TransactionResponse cancel(Long callerUserId, String reference) {
        Transaction transaction = findByReference(reference);
        if (!transaction.getSenderUserId().equals(callerUserId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the sender can cancel this transaction");
        }
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Only PENDING transactions can be cancelled");
        }
        transaction.setStatus(TransactionStatus.FAILED);
        transactionRepository.save(transaction);
        return TransactionMapper.toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse status(Long callerUserId, boolean admin, String reference) {
        return TransactionMapper.toResponse(loadForCaller(callerUserId, admin, reference));
    }

    @Transactional(readOnly = true)
    public TransactionResponse detail(Long callerUserId, boolean admin, String reference) {
        return TransactionMapper.toResponse(loadForCaller(callerUserId, admin, reference));
    }

    private Transaction loadForCaller(Long callerUserId, boolean admin, String reference) {
        Transaction transaction = findByReference(reference);
        boolean participant = transaction.getSenderUserId().equals(callerUserId)
                || transaction.getReceiverUserId().equals(callerUserId);
        if (!admin && !participant) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return transaction;
    }

    private Transaction findByReference(String reference) {
        return transactionRepository.findByReference(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    private TransactionResponse replay(Transaction existing, Long receiverUserId, BigDecimal amount,
                                       String currency, TransactionType type) {
        boolean same = existing.getReceiverUserId().equals(receiverUserId)
                && existing.getAmount().compareTo(amount) == 0
                && existing.getCurrency().equals(currency)
                && existing.getType() == type;
        if (!same) {
            throw new ApiException(ErrorCode.DUPLICATE_REQUEST,
                    "Idempotency key was already used with a different payload");
        }
        return TransactionMapper.toResponse(existing);
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

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return MoneySupport.DEFAULT_CURRENCY;
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "currency must be a 3-letter code");
        }
        return normalized;
    }

    private RiskLevel parseRiskLevel(String level) {
        try {
            return RiskLevel.valueOf(level.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Unsupported risk level from screener");
        }
    }

    private String descriptionOrDefault(String description, TransactionType type) {
        if (description == null || description.isBlank()) {
            return "Simulated " + type.name().toLowerCase(Locale.ROOT);
        }
        return description.trim();
    }

    private Map<String, Object> transactionEventPayload(Transaction transaction) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transaction.getId());
        payload.put("reference", transaction.getReference());
        payload.put("type", transaction.getType().name());
        payload.put("status", transaction.getStatus().name());
        payload.put("amount", transaction.getAmount().toPlainString());
        payload.put("currency", transaction.getCurrency());
        payload.put("senderUserId", transaction.getSenderUserId());
        payload.put("receiverUserId", transaction.getReceiverUserId());
        payload.put("riskLevel", transaction.getRiskLevel().name());
        payload.put("riskScore", transaction.getRiskScore());
        return payload;
    }

    private String nextReference() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String random = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        return "TXN-" + date + "-" + random;
    }
}
