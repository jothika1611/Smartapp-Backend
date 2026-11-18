package com.smartpay.wallet.service;

import com.smartpay.shared.dto.PageResponse;
import com.smartpay.shared.exception.ApiException;
import com.smartpay.wallet.dto.LedgerEntryResponse;
import com.smartpay.wallet.dto.WalletResponse;
import com.smartpay.wallet.dto.WalletTransferResult;
import com.smartpay.wallet.entity.LedgerEntryType;
import com.smartpay.wallet.entity.Wallet;
import com.smartpay.wallet.entity.WalletLedgerEntry;
import com.smartpay.wallet.repository.WalletLedgerEntryRepository;
import com.smartpay.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerEntryRepository ledgerRepository;

    @InjectMocks
    private WalletService walletService;

    private Wallet wallet(String balance) {
        return wallet(1L, 5L, balance);
    }

    private Wallet wallet(Long id, Long userId, String balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setUserId(userId);
        wallet.setCurrency("INR");
        wallet.setAvailableBalance(new BigDecimal(balance));
        wallet.setLedgerBalance(new BigDecimal(balance));
        return wallet;
    }

    private WalletLedgerEntry ledgerEntry(LedgerEntryType type, String amount, String balanceAfter) {
        WalletLedgerEntry entry = new WalletLedgerEntry();
        entry.setWalletId(1L);
        entry.setEntryType(type);
        entry.setAmount(new BigDecimal(amount));
        entry.setBalanceAfter(new BigDecimal(balanceAfter));
        return entry;
    }

    @Test
    void getWallet_createsZeroBalanceWalletWhenMissing() {
        when(walletRepository.findByUserId(5L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        WalletResponse response = walletService.getWallet(5L);

        assertThat(response.getUserId()).isEqualTo(5L);
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("0.00");
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("0.00");
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void getWallet_returnsExistingWalletWithoutCreatingAnother() {
        when(walletRepository.findByUserId(5L)).thenReturn(Optional.of(wallet("42.00")));

        WalletResponse response = walletService.getWallet(5L);

        assertThat(response.getAvailableBalance()).isEqualByComparingTo("42.00");
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void credit_locksWalletUpdatesBothBalancesAndAppendsCredit() {
        Wallet wallet = wallet("100.00");
        when(walletRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.credit(5L, new BigDecimal("25.50"), 99L, "Demo funding");

        assertThat(response.getAvailableBalance()).isEqualByComparingTo("125.50");
        assertThat(response.getLedgerBalance()).isEqualByComparingTo("125.50");

        ArgumentCaptor<WalletLedgerEntry> captor = ArgumentCaptor.forClass(WalletLedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        WalletLedgerEntry entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(entry.getAmount()).isEqualByComparingTo("25.50");
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("125.50");
        assertThat(entry.getTransactionId()).isEqualTo(99L);
        assertThat(entry.getDescription()).isEqualTo("Demo funding");
    }

    @Test
    void debit_updatesBothBalancesAndAppendsDebit() {
        Wallet wallet = wallet("100.00");
        when(walletRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.debit(5L, new BigDecimal("40.25"), null, "Test debit");

        assertThat(response.getAvailableBalance()).isEqualByComparingTo("59.75");

        ArgumentCaptor<WalletLedgerEntry> captor = ArgumentCaptor.forClass(WalletLedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("40.25");
    }

    @Test
    void debit_rejectsInsufficientFundsWithoutWritingLedgerOrChangingBalance() {
        Wallet wallet = wallet("10.00");
        when(walletRepository.findByUserIdForUpdate(5L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.debit(5L, new BigDecimal("10.01"), null, "Too much"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient");

        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("10.00");
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void credit_rejectsZeroNegativeAndOverPrecisionAmounts() {
        assertThatThrownBy(() -> walletService.credit(5L, BigDecimal.ZERO, null, "x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("greater than zero");

        assertThatThrownBy(() -> walletService.credit(5L, new BigDecimal("-1.00"), null, "x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("greater than zero");

        assertThatThrownBy(() -> walletService.credit(5L, new BigDecimal("1.001"), null, "x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("2 decimal places");

        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void ensureWallet_doesNothingWhenWalletAlreadyExists() {
        when(walletRepository.findByUserId(5L)).thenReturn(Optional.of(wallet("0.00")));

        walletService.ensureWallet(5L);

        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void getLedger_mapsEntriesForTheUsersWallet() {
        Wallet wallet = wallet("50.00");
        when(walletRepository.findByUserId(5L)).thenReturn(Optional.of(wallet));
        when(ledgerRepository.findByWalletIdOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ledgerEntry(LedgerEntryType.CREDIT, "50.00", "50.00"))));

        PageResponse<LedgerEntryResponse> response =
                walletService.getLedger(5L, PageRequest.of(0, 20));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getEntryType()).isEqualTo("CREDIT");
        assertThat(response.getContent().get(0).getBalanceAfter()).isEqualByComparingTo("50.00");
    }

    @Test
    void transfer_locksBothWalletsInAscendingUserIdOrderAndWritesBothLedgerLines() {
        Wallet receiver = wallet(10L, 4L, "100.00");
        Wallet sender = wallet(11L, 9L, "50.00");
        when(walletRepository.findByUserIdForUpdate(4L)).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUserIdForUpdate(9L)).thenReturn(Optional.of(sender));

        WalletTransferResult result = walletService.transfer(
                9L, 4L, new BigDecimal("30.00"), 77L, "Simulated transfer sent", "Simulated transfer received");

        assertThat(result.getSenderWallet().getAvailableBalance()).isEqualByComparingTo("20.00");
        assertThat(result.getReceiverWallet().getAvailableBalance()).isEqualByComparingTo("130.00");

        ArgumentCaptor<WalletLedgerEntry> captor = ArgumentCaptor.forClass(WalletLedgerEntry.class);
        verify(ledgerRepository, times(2)).save(captor.capture());
        WalletLedgerEntry debit = captor.getAllValues().stream()
                .filter(entry -> entry.getWalletId().equals(11L)).findFirst().orElseThrow();
        WalletLedgerEntry credit = captor.getAllValues().stream()
                .filter(entry -> entry.getWalletId().equals(10L)).findFirst().orElseThrow();
        assertThat(debit.getEntryType()).isEqualTo(LedgerEntryType.DEBIT);
        assertThat(debit.getBalanceAfter()).isEqualByComparingTo("20.00");
        assertThat(credit.getEntryType()).isEqualTo(LedgerEntryType.CREDIT);
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("130.00");
        assertThat(credit.getTransactionId()).isEqualTo(77L);

        InOrder order = inOrder(walletRepository);
        order.verify(walletRepository).findByUserIdForUpdate(4L);
        order.verify(walletRepository).findByUserIdForUpdate(9L);
    }

    @Test
    void transfer_rejectsInsufficientFundsWithoutAnyLedgerWrite() {
        Wallet receiver = wallet(10L, 4L, "100.00");
        Wallet sender = wallet(11L, 9L, "10.00");
        when(walletRepository.findByUserIdForUpdate(4L)).thenReturn(Optional.of(receiver));
        when(walletRepository.findByUserIdForUpdate(9L)).thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> walletService.transfer(
                9L, 4L, new BigDecimal("20.00"), 77L, "sent", "received"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Insufficient");

        assertThat(sender.getAvailableBalance()).isEqualByComparingTo("10.00");
        assertThat(receiver.getAvailableBalance()).isEqualByComparingTo("100.00");
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void transfer_rejectsSelfTransfer() {
        assertThatThrownBy(() -> walletService.transfer(
                5L, 5L, new BigDecimal("10.00"), null, "sent", "received"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different");

        verify(walletRepository, never()).findByUserIdForUpdate(any());
    }
}
