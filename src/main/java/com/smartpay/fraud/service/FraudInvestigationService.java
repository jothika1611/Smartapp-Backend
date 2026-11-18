package com.smartpay.fraud.service;

import com.smartpay.fraud.dto.AdminInvestigationResponse;
import com.smartpay.fraud.dto.FraudAssessmentResponse;
import com.smartpay.fraud.entity.FraudAssessment;
import com.smartpay.fraud.entity.FraudRule;
import com.smartpay.fraud.entity.FraudRuleHit;
import com.smartpay.fraud.repository.FraudAssessmentRepository;
import com.smartpay.fraud.repository.FraudRuleHitRepository;
import com.smartpay.fraud.repository.FraudRuleRepository;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.mapper.TransactionMapper;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Builds the ADMIN investigation payload without exposing JPA entities. */
@Service
public class FraudInvestigationService {

    private final TransactionRepository transactionRepository;
    private final FraudAssessmentRepository assessmentRepository;
    private final FraudRuleHitRepository hitRepository;
    private final FraudRuleRepository ruleRepository;

    public FraudInvestigationService(TransactionRepository transactionRepository,
                                     FraudAssessmentRepository assessmentRepository,
                                     FraudRuleHitRepository hitRepository,
                                     FraudRuleRepository ruleRepository) {
        this.transactionRepository = transactionRepository;
        this.assessmentRepository = assessmentRepository;
        this.hitRepository = hitRepository;
        this.ruleRepository = ruleRepository;
    }

    @Transactional(readOnly = true)
    public AdminInvestigationResponse get(String reference) {
        Transaction transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        FraudAssessmentResponse assessment = assessmentRepository.findByTransactionId(transaction.getId())
                .map(this::toResponse)
                .orElse(null);
        return new AdminInvestigationResponse(TransactionMapper.toResponse(transaction), assessment);
    }

    private FraudAssessmentResponse toResponse(FraudAssessment assessment) {
        List<FraudAssessmentResponse.RuleHitResponse> hits =
                hitRepository.findByTransactionIdOrderByIdAsc(assessment.getTransactionId()).stream()
                        .map(this::toHit)
                        .toList();
        return new FraudAssessmentResponse(
                null,
                assessment.getScore(),
                assessment.getRiskLevel(),
                assessment.getExplanationDeterministic(),
                assessment.getExplanationAi(),
                assessment.getAiSource(),
                hits);
    }

    private FraudAssessmentResponse.RuleHitResponse toHit(FraudRuleHit hit) {
        FraudRule rule = ruleRepository.findById(hit.getRuleId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Rule hit has no rule"));
        return new FraudAssessmentResponse.RuleHitResponse(
                rule.getCode(), hit.getPoints(), hit.getDetail());
    }
}
