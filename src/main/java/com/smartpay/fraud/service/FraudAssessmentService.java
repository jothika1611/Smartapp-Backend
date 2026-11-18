package com.smartpay.fraud.service;

import com.smartpay.ai.AiExplanationService;
import com.smartpay.ai.ExplanationRewriter;
import com.smartpay.fraud.entity.FraudAlert;
import com.smartpay.fraud.entity.FraudAssessment;
import com.smartpay.fraud.entity.FraudRule;
import com.smartpay.fraud.entity.FraudRuleHit;
import com.smartpay.fraud.repository.FraudAlertRepository;
import com.smartpay.fraud.repository.FraudAssessmentRepository;
import com.smartpay.fraud.repository.FraudRuleHitRepository;
import com.smartpay.fraud.repository.FraudRuleRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.shared.fraud.FraudDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Writes the deterministic assessment, evidence hits and HIGH alerts in the caller's transaction. */
@Service
public class FraudAssessmentService {

    private final FraudAssessmentRepository assessmentRepository;
    private final FraudRuleHitRepository hitRepository;
    private final FraudRuleRepository ruleRepository;
    private final FraudAlertRepository alertRepository;
    private final AiExplanationService aiExplanationService;

    public FraudAssessmentService(FraudAssessmentRepository assessmentRepository,
                                  FraudRuleHitRepository hitRepository,
                                  FraudRuleRepository ruleRepository,
                                  FraudAlertRepository alertRepository,
                                  AiExplanationService aiExplanationService) {
        this.assessmentRepository = assessmentRepository;
        this.hitRepository = hitRepository;
        this.ruleRepository = ruleRepository;
        this.alertRepository = alertRepository;
        this.aiExplanationService = aiExplanationService;
    }

    /** Joins the existing payment transaction; a failure here rolls the payment back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public FraudAssessment persist(Long transactionId, FraudDecision decision) {
        if (decision == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Missing fraud decision");
        }

        FraudAssessment assessment = new FraudAssessment();
        assessment.setTransactionId(transactionId);
        assessment.setScore(decision.score());
        assessment.setRiskLevel(decision.riskLevel());
        assessment.setExplanationDeterministic(decision.explanation());
        ExplanationRewriter.Result ai = aiExplanationService.rewrite(
                decision.explanation(), decision.score(), decision.riskLevel());
        assessment.setExplanationAi(ai.text());
        assessment.setAiSource(ai.source());
        FraudAssessment saved = assessmentRepository.save(assessment);

        for (FraudDecision.RuleHit hit : decision.hits()) {
            FraudRule rule = ruleRepository.findByCode(hit.ruleCode())
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                            "Unknown fraud rule: " + hit.ruleCode()));
            FraudRuleHit entity = new FraudRuleHit();
            entity.setTransactionId(transactionId);
            entity.setRuleId(rule.getId());
            entity.setDetail(Objects.requireNonNullElse(hit.detail(), ""));
            entity.setPoints(hit.points());
            hitRepository.save(entity);
        }

        if ("HIGH".equals(decision.riskLevel())) {
            FraudAlert alert = new FraudAlert();
            alert.setTransactionId(transactionId);
            alert.setSeverity("HIGH");
            alert.setStatus("OPEN");
            alert.setRiskScore(decision.score());
            alert.setReasonSummary(decision.explanation());
            alertRepository.save(alert);
        }
        return saved;
    }
}
