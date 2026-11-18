package com.smartpay.payment.service;

import com.smartpay.fraud.entity.FraudAlert;
import com.smartpay.fraud.repository.FraudAlertRepository;
import com.smartpay.event.config.EventProperties;
import com.smartpay.event.service.OutboxService;
import com.smartpay.payment.dto.TransactionResponse;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.mapper.TransactionMapper;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ADMIN-only state transitions for held or pending simulated transactions. The transaction row is
 * locked first, so concurrent decisions cannot release the same money twice.
 */
@Service
public class AdminReviewService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final FraudAlertRepository alertRepository;
    private final OutboxService outboxService;
    private final EventProperties eventProperties;

    public AdminReviewService(TransactionRepository transactionRepository,
                              WalletService walletService,
                              FraudAlertRepository alertRepository,
                              OutboxService outboxService,
                              EventProperties eventProperties) {
        this.transactionRepository = transactionRepository;
        this.walletService = walletService;
        this.alertRepository = alertRepository;
        this.outboxService = outboxService;
        this.eventProperties = eventProperties;
    }

    @Transactional
    public TransactionResponse approve(Long adminUserId, String reference) {
        Transaction transaction = loadReviewable(reference);
        String previousStatus = transaction.getStatus().name();
        String typeLabel = transaction.getType().name().toLowerCase(Locale.ROOT);
        walletService.transfer(
                transaction.getSenderUserId(),
                transaction.getReceiverUserId(),
                transaction.getAmount(),
                transaction.getId(),
                "Simulated " + typeLabel + " approved by admin",
                "Simulated " + typeLabel + " approved by admin");
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setProcessedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        resolveLinkedAlerts(transaction, adminUserId, "Automatically resolved after ADMIN approve");
        writeStatusEvent(transaction, previousStatus, "APPROVED", adminUserId);
        return TransactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse reject(Long adminUserId, String reference) {
        Transaction transaction = loadReviewable(reference);
        String previousStatus = transaction.getStatus().name();
        transaction.setStatus(TransactionStatus.REJECTED);
        transactionRepository.save(transaction);
        resolveLinkedAlerts(transaction, adminUserId, "Automatically resolved after ADMIN reject");
        writeStatusEvent(transaction, previousStatus, "REJECTED", adminUserId);
        return TransactionMapper.toResponse(transaction);
    }

    private Transaction loadReviewable(String reference) {
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        if (transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getStatus() != TransactionStatus.HOLD) {
            throw new ApiException(ErrorCode.INVALID_STATE,
                    "Only PENDING or HOLD transactions can be reviewed");
        }
        return transaction;
    }

    private void resolveLinkedAlerts(Transaction transaction, Long adminUserId, String note) {
        for (FraudAlert alert : alertRepository.findByTransactionId(transaction.getId())) {
            if ("RESOLVED".equals(alert.getStatus())) {
                continue;
            }
            alert.setStatus("RESOLVED");
            if (alert.getAssignedAdminId() == null) {
                alert.setAssignedAdminId(adminUserId);
            }
            alert.setResolutionNotes(note);
            alert.setResolvedAt(LocalDateTime.now());
            alertRepository.save(alert);
        }
    }

    private void writeStatusEvent(Transaction transaction, String previousStatus,
                                  String decision, Long adminUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", transaction.getId());
        payload.put("reference", transaction.getReference());
        payload.put("previousStatus", previousStatus);
        payload.put("status", transaction.getStatus().name());
        payload.put("decision", decision);
        payload.put("adminUserId", adminUserId);
        payload.put("amount", transaction.getAmount().toPlainString());
        payload.put("currency", transaction.getCurrency());
        payload.put("senderUserId", transaction.getSenderUserId());
        payload.put("receiverUserId", transaction.getReceiverUserId());
        outboxService.write(
                "TRANSACTION", transaction.getReference(), "TRANSACTION_STATUS_CHANGED",
                eventProperties.getTopics().getTxStatus(), "tx-" + transaction.getId(), payload);
    }
}
