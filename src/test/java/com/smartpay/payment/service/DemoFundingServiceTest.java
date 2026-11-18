package com.smartpay.payment.service;

import com.smartpay.payment.dto.DemoFundingRequest;
import com.smartpay.payment.dto.FundingResponse;
import com.smartpay.payment.entity.Transaction;
import com.smartpay.payment.entity.enums.RiskLevel;
import com.smartpay.payment.entity.enums.TransactionStatus;
import com.smartpay.payment.entity.enums.TransactionType;
import com.smartpay.payment.repository.TransactionRepository;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.shared.identity.UserDirectory;
import com.smartpay.wallet.dto.WalletResponse;
import com.smartpay.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoFundingServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private UserDirectory userDirectory;

    @InjectMocks
    private DemoFundingService fundingService;

    private DemoFundingRequest request(Long userId, String amount) {
        DemoFundingRequest request = new DemoFundingRequest();
        request.setUserId(userId);
        request.setAmount(new BigDecimal(amount));
        request.setDescription("Portfolio demo top-up");
        return request;
    }

    private Transaction existingTransaction(Long receiverId, String amount) {
        Transaction transaction = new Transaction();
        transaction.setId(55L);
        transaction.setReference("FND-EXISTING");
        transaction.setSenderUserId(1L);
        transaction.setReceiverUserId(receiverId);
        transaction.setType(TransactionType.FUNDING);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency("INR");
        transaction.setIdempotencyKey("key-1");
        transaction.setRiskLevel(RiskLevel.LOW);
        return transaction;
    }

    private WalletResponse wallet(String balance) {
        return new WalletResponse(10L, 7L, "INR", new BigDecimal(balance),
                new BigDecimal(balance), null, null);
    }

    @Test
    void fund_requiresIdempotencyKey() {
        assertThatThrownBy(() -> fundingService.fund(1L, "  ", request(7L, "25.00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Idempotency key");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void fund_rejectsUnknownReceiver() {
        when(transactionRepository.findBySenderUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.empty());
        when(userDirectory.userExists(7L)).thenReturn(false);

        assertThatThrownBy(() -> fundingService.fund(1L, "key-1", request(7L, "25.00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Receiver user not found");

        verify(walletService, never()).credit(any(), any(), any(), any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void fund_recordsSuccessTransactionAndCreditsWallet() {
        when(transactionRepository.findBySenderUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.empty());
        when(userDirectory.userExists(7L)).thenReturn(true);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });
        when(walletService.credit(eq(7L), eq(new BigDecimal("25.00")), eq(55L), anyString()))
                .thenReturn(wallet("50.00"));

        FundingResponse response = fundingService.fund(1L, " key-1 ", request(7L, "25.00"));

        assertThat(response.getReference()).startsWith("FND-");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("50.00");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getSenderUserId()).isEqualTo(1L);
        assertThat(saved.getReceiverUserId()).isEqualTo(7L);
        assertThat(saved.getType()).isEqualTo(TransactionType.FUNDING);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.getAmount()).isEqualByComparingTo("25.00");
        assertThat(saved.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(saved.getProcessedAt()).isNotNull();
    }

    @Test
    void fund_replaysSameKeyWithoutCreditingAgain() {
        when(transactionRepository.findBySenderUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(existingTransaction(7L, "25.00")));
        when(walletService.getWallet(7L)).thenReturn(wallet("50.00"));

        FundingResponse response = fundingService.fund(1L, "key-1", request(7L, "25.00"));

        assertThat(response.getReference()).isEqualTo("FND-EXISTING");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("50.00");
        verify(transactionRepository, never()).save(any());
        verify(walletService, never()).credit(any(), any(), any(), any());
    }

    @Test
    void fund_rejectsReusedKeyWithDifferentPayload() {
        when(transactionRepository.findBySenderUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(existingTransaction(7L, "10.00")));

        assertThatThrownBy(() -> fundingService.fund(1L, "key-1", request(7L, "25.00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different payload");

        verify(walletService, never()).getWallet(any());
        verify(walletService, never()).credit(any(), any(), any(), any());
    }

    @Test
    void fund_rejectsInvalidAmountBeforeTouchingDatabase() {
        assertThatThrownBy(() -> fundingService.fund(1L, "key-1", request(7L, "0.00")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("greater than zero");

        verify(transactionRepository, never())
                .findBySenderUserIdAndIdempotencyKey(any(), anyString());
    }
}
