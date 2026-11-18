package com.smartpay.payment.service;

import com.smartpay.event.config.EventProperties;
import com.smartpay.event.service.OutboxService;
import com.smartpay.fraud.entity.FraudAlert;
import com.smartpay.fraud.repository.FraudAlertRepository;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.RiskLevel;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.entity.enums.TransactionType;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.exception.ErrorCode;
import com.smartpay.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReviewServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private FraudAlertRepository alertRepository;

    @Mock
    private OutboxService outboxService;

    @Spy
    private EventProperties eventProperties = new EventProperties();

    @InjectMocks
    private AdminReviewService service;

    private Transaction transaction(TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setId(44L);
        transaction.setReference("TXN-ADMIN-1");
        transaction.setSenderUserId(1L);
        transaction.setReceiverUserId(2L);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(status);
        transaction.setAmount(new BigDecimal("75.00"));
        transaction.setCurrency("INR");
        transaction.setRiskScore(50);
        transaction.setRiskLevel(RiskLevel.HIGH);
        transaction.setDescription("Simulated test");
        return transaction;
    }

    private FraudAlert openAlert() {
        FraudAlert alert = new FraudAlert();
        alert.setId(8L);
        alert.setTransactionId(44L);
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setRiskScore(50);
        alert.setReasonSummary("Simulated test alert");
        return alert;
    }

    @Test
    void approvePendingMovesMoneyAndResolvesLinkedAlert() {
        Transaction transaction = transaction(TransactionStatus.PENDING);
        when(transactionRepository.findByReferenceForUpdate("TXN-ADMIN-1"))
                .thenReturn(Optional.of(transaction));
        when(alertRepository.findByTransactionId(44L)).thenReturn(List.of(openAlert()));

        var response = service.approve(9L, "TXN-ADMIN-1");

        verify(walletService).transfer(eq(1L), eq(2L), eq(new BigDecimal("75.00")), eq(44L),
                anyString(), anyString());
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.getValue().getProcessedAt()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SUCCESS");

        ArgumentCaptor<FraudAlert> alert = ArgumentCaptor.forClass(FraudAlert.class);
        verify(alertRepository).save(alert.capture());
        assertThat(alert.getValue().getStatus()).isEqualTo("RESOLVED");
        assertThat(alert.getValue().getAssignedAdminId()).isEqualTo(9L);
        assertThat(alert.getValue().getResolutionNotes()).contains("ADMIN approve");
        assertThat(alert.getValue().getResolvedAt()).isNotNull();
    }

    @Test
    void approveHoldMovesMoney() {
        Transaction transaction = transaction(TransactionStatus.HOLD);
        when(transactionRepository.findByReferenceForUpdate("TXN-ADMIN-1"))
                .thenReturn(Optional.of(transaction));
        when(alertRepository.findByTransactionId(44L)).thenReturn(List.of());

        service.approve(9L, "TXN-ADMIN-1");

        verify(walletService).transfer(anyLong(), anyLong(), any(BigDecimal.class), anyLong(),
                anyString(), anyString());
    }

    @Test
    void rejectDoesNotMoveMoney() {
        Transaction transaction = transaction(TransactionStatus.HOLD);
        when(transactionRepository.findByReferenceForUpdate("TXN-ADMIN-1"))
                .thenReturn(Optional.of(transaction));
        when(alertRepository.findByTransactionId(44L)).thenReturn(List.of(openAlert()));

        var response = service.reject(9L, "TXN-ADMIN-1");

        verify(walletService, never()).transfer(anyLong(), anyLong(), any(BigDecimal.class), anyLong(),
                anyString(), anyString());
        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(transaction.getProcessedAt()).isNull();
    }

    @Test
    void settledTransactionCannotBeReviewed() {
        Transaction transaction = transaction(TransactionStatus.SUCCESS);
        when(transactionRepository.findByReferenceForUpdate("TXN-ADMIN-1"))
                .thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.approve(9L, "TXN-ADMIN-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATE));

        verify(walletService, never()).transfer(anyLong(), anyLong(), any(BigDecimal.class), anyLong(),
                anyString(), anyString());
    }

    @Test
    void secondApproveCannotMoveMoneyTwice() {
        Transaction first = transaction(TransactionStatus.HOLD);
        Transaction alreadyApproved = transaction(TransactionStatus.SUCCESS);
        when(transactionRepository.findByReferenceForUpdate("TXN-ADMIN-1"))
                .thenReturn(Optional.of(first), Optional.of(alreadyApproved));
        when(alertRepository.findByTransactionId(44L)).thenReturn(List.of());

        service.approve(9L, "TXN-ADMIN-1");

        assertThatThrownBy(() -> service.approve(9L, "TXN-ADMIN-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATE));
        verify(walletService, org.mockito.Mockito.times(1))
                .transfer(anyLong(), anyLong(), any(BigDecimal.class), anyLong(), anyString(), anyString());
    }
}
