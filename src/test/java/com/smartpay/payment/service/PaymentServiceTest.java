package com.smartpay.payment.service;

import com.smartpay.event.config.EventProperties;
import com.smartpay.event.service.OutboxService;
import com.smartpay.payment.dto.PaymentRequest;
import com.smartpay.payment.dto.TransactionResponse;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.RiskLevel;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.entity.enums.TransactionType;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.fraud.service.FraudAssessmentService;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.fraud.FraudDecision;
import com.smartpay.shared.fraud.FraudDecision;
import com.smartpay.shared.fraud.FraudScreener;
import com.smartpay.shared.identity.UserDirectory;
import com.smartpay.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class PaymentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private WalletService walletService;

    @Mock
    private FraudScreener fraudScreener;

    @Mock
    private FraudAssessmentService fraudAssessmentService;

    @Mock
    private OutboxService outboxService;

    @Spy
    private EventProperties eventProperties = new EventProperties();

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest request(String email, String amount) {
        PaymentRequest request = new PaymentRequest();
        request.setReceiverEmail(email);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency("INR");
        request.setDescription("Simulated lunch split");
        return request;
    }

    private Transaction transaction(Long id, String reference, Long sender, Long receiver,
                                    TransactionType type, TransactionStatus status, String amount,
                                    String key) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setReference(reference);
        transaction.setSenderUserId(sender);
        transaction.setReceiverUserId(receiver);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency("INR");
        transaction.setIdempotencyKey(key);
        transaction.setRiskLevel(RiskLevel.LOW);
        return transaction;
    }

    private void mockSaveAssignsId() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(50L);
            }
            return saved;
        });
    }

    private void readyForNewPayment() {
        when(userDirectory.findUserIdByEmail("receiver@smartpay.local")).thenReturn(Optional.of(2L));
        when(transactionRepository.findBySenderUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.empty());
    }

    @Test
    void lowRiskTransferMovesMoneyAndMarksSuccess() {
        readyForNewPayment();
        when(fraudScreener.assess(any())).thenReturn(new FraudDecision(5, "LOW"));
        mockSaveAssignsId();

        TransactionResponse response = paymentService.initiate(
                1L, "key-1", request("receiver@smartpay.local", "30.00"), TransactionType.TRANSFER);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getType()).isEqualTo("TRANSFER");
        assertThat(response.getRiskScore()).isEqualTo(5);
        assertThat(response.getProcessedAt()).isNotNull();
        verify(walletService).transfer(eq(1L), eq(2L), eq(new BigDecimal("30.00")), eq(50L),
                anyString(), anyString());
        verify(fraudAssessmentService).persist(eq(50L), any(FraudDecision.class));
    }

    @Test
    void mediumRiskCreatesPendingWithoutMovingMoney() {
        readyForNewPayment();
        when(fraudScreener.assess(any())).thenReturn(new FraudDecision(45, "MEDIUM"));
        mockSaveAssignsId();

        TransactionResponse response = paymentService.initiate(
                1L, "key-1", request("receiver@smartpay.local", "30.00"), TransactionType.PAYMENT);

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(response.getProcessedAt()).isNull();
        verify(walletService, never()).transfer(anyLong(), anyLong(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void highRiskCreatesHoldWithoutMovingMoney() {
        readyForNewPayment();
        when(fraudScreener.assess(any())).thenReturn(new FraudDecision(90, "HIGH"));
        mockSaveAssignsId();

        TransactionResponse response = paymentService.initiate(
                1L, "key-1", request("receiver@smartpay.local", "30.00"), TransactionType.TRANSFER);

        assertThat(response.getStatus()).isEqualTo("HOLD");
        verify(walletService, never()).transfer(anyLong(), anyLong(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void replaySameKeyReturnsOriginalWithoutSecondTransfer() {
        when(userDirectory.findUserIdByEmail("receiver@smartpay.local")).thenReturn(Optional.of(2L));
        when(transactionRepository.findBySenderUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(transaction(50L, "TXN-REPLAY", 1L, 2L,
                        TransactionType.TRANSFER, TransactionStatus.SUCCESS, "30.00", "key-1")));

        TransactionResponse response = paymentService.initiate(
                1L, "key-1", request("receiver@smartpay.local", "30.00"), TransactionType.TRANSFER);

        assertThat(response.getReference()).isEqualTo("TXN-REPLAY");
        verify(transactionRepository, never()).save(any());
        verify(walletService, never()).transfer(anyLong(), anyLong(), any(), anyLong(), anyString(), anyString());
    }

    @Test
    void replaySameKeyWithDifferentPayloadIsRejected() {
        when(userDirectory.findUserIdByEmail("receiver@smartpay.local")).thenReturn(Optional.of(2L));
        when(transactionRepository.findBySenderUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(transaction(50L, "TXN-REPLAY", 1L, 2L,
                        TransactionType.TRANSFER, TransactionStatus.SUCCESS, "10.00", "key-1")));

        assertThatThrownBy(() -> paymentService.initiate(
                1L, "key-1", request("receiver@smartpay.local", "30.00"), TransactionType.TRANSFER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different payload");
    }

    @Test
    void unknownReceiverIsRejectedBeforeSaving() {
        when(userDirectory.findUserIdByEmail("missing@smartpay.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.initiate(
                1L, "key-1", request("missing@smartpay.local", "30.00"), TransactionType.TRANSFER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Receiver user not found");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> paymentService.initiate(
                1L, null, request("receiver@smartpay.local", "30.00"), TransactionType.TRANSFER))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Idempotency key");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void senderCanCancelOnlyPendingTransaction() {
        Transaction pending = transaction(50L, "TXN-PENDING", 1L, 2L,
                TransactionType.PAYMENT, TransactionStatus.PENDING, "10.00", "key-1");
        when(transactionRepository.findByReference("TXN-PENDING")).thenReturn(Optional.of(pending));

        TransactionResponse response = paymentService.cancel(1L, "TXN-PENDING");

        assertThat(response.getStatus()).isEqualTo("FAILED");
        verify(transactionRepository).save(pending);
    }

    @Test
    void unrelatedUserCannotCancelAndSuccessCannotBeCancelled() {
        Transaction pending = transaction(50L, "TXN-PENDING", 1L, 2L,
                TransactionType.PAYMENT, TransactionStatus.PENDING, "10.00", "key-1");
        when(transactionRepository.findByReference("TXN-PENDING")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> paymentService.cancel(2L, "TXN-PENDING"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only the sender");

        Transaction success = transaction(51L, "TXN-SUCCESS", 1L, 2L,
                TransactionType.PAYMENT, TransactionStatus.SUCCESS, "10.00", "key-2");
        when(transactionRepository.findByReference("TXN-SUCCESS")).thenReturn(Optional.of(success));

        assertThatThrownBy(() -> paymentService.cancel(1L, "TXN-SUCCESS"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void statusIsVisibleToParticipantsAndAdminOnly() {
        Transaction success = transaction(50L, "TXN-OK", 1L, 2L,
                TransactionType.TRANSFER, TransactionStatus.SUCCESS, "10.00", "key-1");
        when(transactionRepository.findByReference("TXN-OK")).thenReturn(Optional.of(success));

        assertThat(paymentService.status(2L, false, "TXN-OK").getReference()).isEqualTo("TXN-OK");
        assertThat(paymentService.status(1L, true, "TXN-OK").getReference()).isEqualTo("TXN-OK");

        assertThatThrownBy(() -> paymentService.status(3L, false, "TXN-OK"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void saveRecordsComputedRiskFields() {
        readyForNewPayment();
        when(fraudScreener.assess(any())).thenReturn(new FraudDecision(70, "HIGH"));
        mockSaveAssignsId();

        paymentService.initiate(1L, "key-1",
                request("receiver@smartpay.local", "30.00"), TransactionType.TRANSFER);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getReference()).startsWith("TXN-");
        assertThat(saved.getRiskScore()).isEqualTo(70);
        assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(saved.getIdempotencyKey()).isEqualTo("key-1");
    }
}
