package com.smartpay.fraud.service;

import com.smartpay.fraud.config.FraudProperties;
import com.smartpay.fraud.entity.FraudRule;
import com.smartpay.fraud.repository.FraudRuleRepository;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.fraud.FraudContext;
import com.smartpay.shared.fraud.FraudDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudRuleEngineTest {

    @Mock
    private FraudRuleRepository ruleRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private FraudProperties properties;
    private FraudRuleEngine engine;

    @BeforeEach
    void setUp() {
        properties = new FraudProperties();
        engine = new FraudRuleEngine(ruleRepository, transactionRepository, properties);
    }

    private FraudRule rule(String code, int maxPoints, String threshold, Integer windowMinutes) {
        FraudRule rule = new FraudRule();
        rule.setId(1L);
        rule.setCode(code);
        rule.setName(code);
        rule.setEnabled(true);
        rule.setMaxPoints(maxPoints);
        rule.setThresholdValue(threshold == null ? null : new BigDecimal(threshold));
        rule.setTimeWindowMinutes(windowMinutes);
        return rule;
    }

    private FraudContext context(String amount, String device, String location) {
        return new FraudContext(1L, 2L, new BigDecimal(amount), "INR", "TRANSFER",
                device, location, "Simulated test");
    }

    @Test
    void noEnabledRulesProducesLowWithNoHits() {
        when(ruleRepository.findAllByEnabledTrue()).thenReturn(List.of());

        FraudDecision decision = engine.assess(context("100.00", null, null));

        assertThat(decision.score()).isZero();
        assertThat(decision.riskLevel()).isEqualTo("LOW");
        assertThat(decision.hits()).isEmpty();
        assertThat(decision.explanation()).contains("No configured risk rule was triggered");
    }

    @Test
    void amountCapNewDeviceAndNewLocationProduceHighWithThreeHits() {
        when(ruleRepository.findAllByEnabledTrue()).thenReturn(List.of(
                rule("AMOUNT_DEVIATION", 25, "3.00", 43200),
                rule("UNFAMILIAR_DEVICE", 15, null, null),
                rule("UNUSUAL_LOCATION", 10, null, null)));
        when(transactionRepository.findBySenderUserIdAndStatusAndCreatedAtAfter(
                eq(1L), eq(TransactionStatus.SUCCESS), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(transactionRepository.existsBySenderUserIdAndDeviceFingerprintAndCreatedAtAfter(
                anyLong(), eq("new-device"), any(LocalDateTime.class))).thenReturn(false);
        when(transactionRepository.existsBySenderUserIdAndLocationHintAndCreatedAtAfter(
                anyLong(), eq("new-place"), any(LocalDateTime.class))).thenReturn(false);

        FraudDecision decision = engine.assess(context("60000.00", "new-device", "new-place"));

        assertThat(decision.score()).isEqualTo(50);
        assertThat(decision.riskLevel()).isEqualTo("HIGH");
        assertThat(decision.hits()).extracting(FraudDecision.RuleHit::ruleCode)
                .containsExactly("AMOUNT_DEVIATION", "UNFAMILIAR_DEVICE", "UNUSUAL_LOCATION");
        assertThat(decision.explanation()).contains("Risk 50/100 (HIGH)");
        assertThat(decision.explanation()).contains("no fraud determination made");
    }

    @Test
    void oneStrongSignalIsCappedIntoMedium() {
        when(ruleRepository.findAllByEnabledTrue()).thenReturn(List.of(
                rule("AMOUNT_DEVIATION", 60, "3.00", 43200)));
        when(transactionRepository.findBySenderUserIdAndStatusAndCreatedAtAfter(
                eq(1L), eq(TransactionStatus.SUCCESS), any(LocalDateTime.class)))
                .thenReturn(List.of());

        FraudDecision decision = engine.assess(context("99999.00", null, null));

        assertThat(decision.hits()).hasSize(1);
        assertThat(decision.score()).isEqualTo(49);
        assertThat(decision.riskLevel()).isEqualTo("MEDIUM");
        assertThat(decision.explanation()).contains("capped at 49");
    }

    @Test
    void velocityAndRepeatRulesUseHistoricalCounts() {
        when(ruleRepository.findAllByEnabledTrue()).thenReturn(List.of(
                rule("VELOCITY", 20, "5.00", 5),
                rule("REPEATED_TRANSACTION", 15, "2.00", 10)));
        when(transactionRepository.countBySenderUserIdAndCreatedAtAfter(
                eq(1L), any(LocalDateTime.class))).thenReturn(7L);
        when(transactionRepository.countBySenderUserIdAndReceiverUserIdAndAmountAndCreatedAtAfter(
                eq(1L), eq(2L), eq(new BigDecimal("100.00")), any(LocalDateTime.class)))
                .thenReturn(2L);

        FraudDecision decision = engine.assess(context("100.00", null, null));

        assertThat(decision.hits()).hasSize(2);
        assertThat(decision.score()).isEqualTo(16);
        assertThat(decision.riskLevel()).isEqualTo("LOW");
        assertThat(decision.explanation()).contains("7 simulated transactions in 5 minutes");
        assertThat(decision.explanation()).contains("repeated 3 times in 10 minutes");
    }

    @Test
    void failedPatternRuleCreatesEvidenceHit() {
        when(ruleRepository.findAllByEnabledTrue()).thenReturn(List.of(
                rule("FAILED_PATTERN", 15, "3.00", 15)));
        when(transactionRepository.countBySenderUserIdAndStatusAndCreatedAtAfter(
                eq(1L), eq(TransactionStatus.FAILED), any(LocalDateTime.class))).thenReturn(4L);

        FraudDecision decision = engine.assess(context("100.00", null, null));

        assertThat(decision.hits()).hasSize(1);
        assertThat(decision.hits().get(0).ruleCode()).isEqualTo("FAILED_PATTERN");
        assertThat(decision.hits().get(0).points()).isEqualTo(5);
        assertThat(decision.explanation()).contains("4 failed simulated attempts");
    }
}
