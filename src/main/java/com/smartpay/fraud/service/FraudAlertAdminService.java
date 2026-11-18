package com.smartpay.fraud.service;

import com.smartpay.fraud.dto.FraudAlertResponse;
import com.smartpay.fraud.entity.FraudAlert;
import com.smartpay.fraud.repository.FraudAlertRepository;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.dto.PageResponse;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.shared.identity.UserDirectory;
import com.smartpay.shared.identity.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Alert queue for ADMIN investigation. Assignment always uses the authenticated ADMIN id. */
@Service
public class FraudAlertAdminService {

    private final FraudAlertRepository alertRepository;
    private final TransactionRepository transactionRepository;
    private final FraudInvestigationService investigationService;
    private final UserDirectory userDirectory;

    public FraudAlertAdminService(FraudAlertRepository alertRepository,
                                  TransactionRepository transactionRepository,
                                  FraudInvestigationService investigationService,
                                  UserDirectory userDirectory) {
        this.alertRepository = alertRepository;
        this.transactionRepository = transactionRepository;
        this.investigationService = investigationService;
        this.userDirectory = userDirectory;
    }

    @Transactional(readOnly = true)
    public PageResponse<FraudAlertResponse> list(int page, int size, String status) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), clampSize(size), Sort.by("id").descending());
        Page<FraudAlert> alerts = status == null || status.isBlank()
                ? alertRepository.findAll(pageable)
                : alertRepository.findByStatus(status.trim().toUpperCase(), pageable);
        return PageResponse.from(alerts.map(alert -> toResponse(alert, false)));
    }

    @Transactional(readOnly = true)
    public FraudAlertResponse detail(Long alertId) {
        return toResponse(load(alertId), true);
    }

    @Transactional
    public FraudAlertResponse assign(Long alertId, Long adminUserId) {
        FraudAlert alert = load(alertId);
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Resolved alerts cannot be reassigned");
        }
        alert.setAssignedAdminId(adminUserId);
        alert.setStatus("INVESTIGATING");
        alertRepository.save(alert);
        return detail(alertId);
    }

    @Transactional
    public FraudAlertResponse resolve(Long alertId, String resolutionNotes, Long adminUserId) {
        FraudAlert alert = load(alertId);
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE, "Alert is already resolved");
        }
        if (resolutionNotes == null || resolutionNotes.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Resolution notes are required");
        }
        if (alert.getAssignedAdminId() == null) {
            alert.setAssignedAdminId(adminUserId);
        }
        alert.setStatus("RESOLVED");
        alert.setResolutionNotes(resolutionNotes.trim());
        alert.setResolvedAt(LocalDateTime.now());
        alertRepository.save(alert);
        return detail(alertId);
    }

    private FraudAlert load(Long alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALERT_NOT_FOUND));
    }

    private FraudAlertResponse toResponse(FraudAlert alert, boolean includeAssessment) {
        Transaction transaction = transactionRepository.findById(alert.getTransactionId()).orElse(null);
        String reference = transaction == null ? "" : transaction.getReference();

        UserSummary sender = transaction == null ? null
                : userDirectory.findSummary(transaction.getSenderUserId()).orElse(null);
        UserSummary receiver = transaction == null ? null
                : userDirectory.findSummary(transaction.getReceiverUserId()).orElse(null);

        var assessment = includeAssessment && reference != null && !reference.isBlank()
                ? investigationService.get(reference).getAssessment()
                : null;
        return new FraudAlertResponse(
                alert.getId(), alert.getTransactionId(), reference,
                displayName(sender, transaction == null ? null : transaction.getSenderUserId()),
                sender == null ? "—" : sender.email(),
                displayName(receiver, transaction == null ? null : transaction.getReceiverUserId()),
                receiver == null ? "—" : receiver.email(),
                alert.getSeverity(), alert.getStatus(), alert.getRiskScore(), alert.getReasonSummary(),
                alert.getAssignedAdminId(), alert.getResolutionNotes(), alert.getCreatedAt(),
                alert.getResolvedAt(), assessment);
    }

    private String displayName(UserSummary user, Long userId) {
        if (user != null && user.fullName() != null && !user.fullName().isBlank()) {
            return user.fullName();
        }
        return userId == null ? "Unknown user" : "User #" + userId;
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(100, size));
    }
}
