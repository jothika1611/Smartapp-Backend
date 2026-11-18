package com.smartpay.fraud.service;

import com.smartpay.fraud.dto.FraudAssessmentResponse;
import com.smartpay.fraud.entity.FraudAssessment;
import com.smartpay.fraud.entity.FraudRule;
import com.smartpay.fraud.entity.FraudRuleHit;
import com.smartpay.fraud.repository.FraudAssessmentRepository;
import com.smartpay.fraud.repository.FraudRuleHitRepository;
import com.smartpay.fraud.repository.FraudRuleRepository;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side for deterministic explanations, scoped to the transaction participant or an ADMIN. */
@Service
public class FraudAssessmentQueryService {

    private final TransactionRepository transactionRepository;
    private final FraudAssessmentRepository assessmentRepository;
    private final FraudRuleHitRepository hitRepository;
    private final FraudRuleRepository ruleRepository;

    public FraudAssessmentQueryService(TransactionRepository transactionRepository,
                                       FraudAssessmentRepository assessmentRepository,
                                       FraudRuleHitRepository hitRepository,
                                       FraudRuleRepository ruleRepository) {
        this.transactionRepository = transactionRepository;
        this.assessmentRepository = assessmentRepository;
        this.hitRepository = hitRepository;
        this.ruleRepository = ruleRepository;
    }

    @Transactional(readOnly = true)
    public FraudAssessmentResponse get(Long callerUserId, boolean admin, String reference) {
        Transaction transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        boolean participant = transaction.getSenderUserId().equals(callerUserId)
                || transaction.getReceiverUserId().equals(callerUserId);
        if (!admin && !participant) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        FraudAssessment assessment = assessmentRepository.findByTransactionId(transaction.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND,
                        "Assessment not found"));
        List<FraudAssessmentResponse.RuleHitResponse> hits =
                hitRepository.findByTransactionIdOrderByIdAsc(transaction.getId()).stream()
                        .map(this::toHitResponse)
                        .toList();

        return new FraudAssessmentResponse(
                transaction.getReference(),
                assessment.getScore(),
                assessment.getRiskLevel(),
                assessment.getExplanationDeterministic(),
                assessment.getExplanationAi(),
                assessment.getAiSource(),
                hits);
    }

    private FraudAssessmentResponse.RuleHitResponse toHitResponse(FraudRuleHit hit) {
        FraudRule rule = ruleRepository.findById(hit.getRuleId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Rule hit has no rule"));
        return new FraudAssessmentResponse.RuleHitResponse(
                rule.getCode(), hit.getPoints(), hit.getDetail());
    }
}
