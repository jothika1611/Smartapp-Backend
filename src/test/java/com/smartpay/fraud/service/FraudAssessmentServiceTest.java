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
import com.smartpay.shared.fraud.FraudDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudAssessmentServiceTest {

    @Mock
    private FraudAssessmentRepository assessmentRepository;
    @Mock
    private FraudRuleHitRepository hitRepository;
    @Mock
    private FraudRuleRepository ruleRepository;
    @Mock
    private FraudAlertRepository alertRepository;
    @Mock
    private AiExplanationService aiExplanationService;

    @InjectMocks
    private FraudAssessmentService service;

    @Test
    void highDecisionSavesAssessmentHitsAndAnOpenAlert() {
        when(assessmentRepository.save(any(FraudAssessment.class))).thenAnswer(invocation -> {
            FraudAssessment assessment = invocation.getArgument(0);
            assessment.setId(5L);
            return assessment;
        });
        FraudRule rule = new FraudRule();
        rule.setId(9L);
        rule.setCode("AMOUNT_DEVIATION");
        when(ruleRepository.findByCode("AMOUNT_DEVIATION")).thenReturn(Optional.of(rule));

        FraudDecision decision = new FraudDecision(55, "HIGH", "Risk 55/100 (HIGH).",
                List.of(new FraudDecision.RuleHit("AMOUNT_DEVIATION", 25, "Above cap.")));
        when(aiExplanationService.rewrite(anyString(), anyInt(), anyString()))
                .thenReturn(new ExplanationRewriter.Result("AI wording", "MOCK"));

        service.persist(77L, decision);

        ArgumentCaptor<FraudAssessment> assessment = ArgumentCaptor.forClass(FraudAssessment.class);
        verify(assessmentRepository).save(assessment.capture());
        assertThat(assessment.getValue().getTransactionId()).isEqualTo(77L);
        assertThat(assessment.getValue().getScore()).isEqualTo(55);
        assertThat(assessment.getValue().getRiskLevel()).isEqualTo("HIGH");
        assertThat(assessment.getValue().getExplanationDeterministic())
                .isEqualTo("Risk 55/100 (HIGH).");
        assertThat(assessment.getValue().getExplanationAi()).isEqualTo("AI wording");
        assertThat(assessment.getValue().getAiSource()).isEqualTo("MOCK");

        ArgumentCaptor<FraudRuleHit> hit = ArgumentCaptor.forClass(FraudRuleHit.class);
        verify(hitRepository).save(hit.capture());
        assertThat(hit.getValue().getRuleId()).isEqualTo(9L);
        assertThat(hit.getValue().getPoints()).isEqualTo(25);

        ArgumentCaptor<FraudAlert> alert = ArgumentCaptor.forClass(FraudAlert.class);
        verify(alertRepository).save(alert.capture());
        assertThat(alert.getValue().getTransactionId()).isEqualTo(77L);
        assertThat(alert.getValue().getSeverity()).isEqualTo("HIGH");
        assertThat(alert.getValue().getStatus()).isEqualTo("OPEN");
    }

    @Test
    void lowDecisionSavesAssessmentButNoAlert() {
        when(assessmentRepository.save(any(FraudAssessment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(aiExplanationService.rewrite(anyString(), anyInt(), anyString()))
                .thenReturn(new ExplanationRewriter.Result("AI wording", "MOCK"));
        service.persist(78L, new FraudDecision(0, "LOW", "No rules triggered.", List.of()));

        verify(alertRepository, never()).save(any());
        verify(hitRepository, never()).save(any());
    }
}
