package com.smartpay.fraud.service;

import com.smartpay.fraud.dto.AdminInvestigationResponse;
import com.smartpay.fraud.dto.FraudRuleRequest;
import com.smartpay.fraud.entity.FraudAlert;
import com.smartpay.fraud.entity.FraudRule;
import com.smartpay.fraud.repository.FraudAlertRepository;
import com.smartpay.fraud.repository.FraudRuleRepository;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.shared.identity.UserDirectory;
import com.smartpay.shared.identity.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFraudServiceTest {

    @Mock
    private FraudAlertRepository alertRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private FraudInvestigationService investigationService;
    @Mock
    private FraudRuleRepository ruleRepository;
    @Mock
    private UserDirectory userDirectory;

    private FraudAlertAdminService alertService;
    private FraudRuleAdminService ruleService;

    @BeforeEach
    void setUp() {
        alertService = new FraudAlertAdminService(alertRepository, transactionRepository,
                investigationService, userDirectory);
        ruleService = new FraudRuleAdminService(ruleRepository);
    }

    @Test
    void assignAndResolveUseAuthenticatedAdminId() {
        FraudAlert alert = new FraudAlert();
        alert.setId(7L);
        alert.setTransactionId(44L);
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setRiskScore(50);
        alert.setReasonSummary("Test alert");
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Transaction transaction = new Transaction();
        transaction.setId(44L);
        transaction.setReference("TXN-ALERT-1");
        transaction.setSenderUserId(5L);
        transaction.setReceiverUserId(6L);
        when(transactionRepository.findById(44L)).thenReturn(Optional.of(transaction));
        when(userDirectory.findSummary(5L))
                .thenReturn(Optional.of(new UserSummary(5L, "Sender Customer", "sender@smartpay.local")));
        when(userDirectory.findSummary(6L))
                .thenReturn(Optional.of(new UserSummary(6L, "Receiver Customer", "receiver@smartpay.local")));
        when(investigationService.get("TXN-ALERT-1"))
                .thenReturn(new AdminInvestigationResponse(null, null));

        var assigned = alertService.assign(7L, 9L);
        assertThat(assigned.getStatus()).isEqualTo("INVESTIGATING");
        assertThat(assigned.getAssignedAdminId()).isEqualTo(9L);
        assertThat(assigned.getSenderName()).isEqualTo("Sender Customer");
        assertThat(assigned.getSenderEmail()).isEqualTo("sender@smartpay.local");
        assertThat(assigned.getReceiverName()).isEqualTo("Receiver Customer");
        assertThat(assigned.getReceiverEmail()).isEqualTo("receiver@smartpay.local");

        var resolved = alertService.resolve(7L, "Reviewed simulated evidence", 10L);
        assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
        assertThat(resolved.getResolutionNotes()).isEqualTo("Reviewed simulated evidence");
        assertThat(resolved.getResolvedAt()).isNotNull();
        // The original assignee is preserved; the resolving ADMIN does not overwrite history.
        assertThat(alert.getAssignedAdminId()).isEqualTo(9L);

        assertThatThrownBy(() -> alertService.resolve(7L, "Again", 10L))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATE));
    }

    @Test
    void ruleCreateUpdateAndDisableWorkOnEntityNotDto() {
        when(ruleRepository.existsByCode("CUSTOM_RULE")).thenReturn(false);
        when(ruleRepository.save(any(FraudRule.class))).thenAnswer(invocation -> {
            FraudRule rule = invocation.getArgument(0);
            rule.setId(12L);
            return rule;
        });

        FraudRuleRequest create = new FraudRuleRequest();
        create.setCode("custom_rule");
        create.setName("Custom simulated rule");
        create.setMaxPoints(10);
        create.setThresholdValue(new BigDecimal("2.00"));
        create.setTimeWindowMinutes(15);
        create.setEnabled(true);

        var created = ruleService.create(create);
        assertThat(created.getId()).isEqualTo(12L);
        assertThat(created.getCode()).isEqualTo("CUSTOM_RULE");
        assertThat(created.isEnabled()).isTrue();

        FraudRule stored = new FraudRule();
        stored.setId(12L);
        stored.setCode("CUSTOM_RULE");
        stored.setName("Custom simulated rule");
        stored.setMaxPoints(10);
        stored.setEnabled(true);
        when(ruleRepository.findById(12L)).thenReturn(Optional.of(stored));

        FraudRuleRequest update = new FraudRuleRequest();
        update.setCode("IGNORED");
        update.setName("Renamed simulated rule");
        update.setMaxPoints(20);
        update.setEnabled(true);
        var updated = ruleService.update(12L, update);
        assertThat(updated.getCode()).isEqualTo("CUSTOM_RULE");
        assertThat(updated.getMaxPoints()).isEqualTo(20);
        assertThat(updated.getThresholdValue()).isNull();

        var disabled = ruleService.disable(12L);
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void duplicateRuleCodeIsRejected() {
        when(ruleRepository.existsByCode("DUPLICATE")).thenReturn(true);
        FraudRuleRequest request = new FraudRuleRequest();
        request.setCode("duplicate");
        request.setName("Duplicate");
        request.setMaxPoints(5);
        request.setEnabled(true);

        assertThatThrownBy(() -> ruleService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
                        .isEqualTo(ErrorCode.RULE_CODE_EXISTS));
    }
}
