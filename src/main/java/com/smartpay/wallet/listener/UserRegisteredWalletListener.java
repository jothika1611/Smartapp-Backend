package com.smartpay.wallet.listener;

import com.smartpay.shared.event.UserRegisteredEvent;
import com.smartpay.wallet.service.WalletService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs inside the registration transaction. If wallet creation fails, registration rolls back too,
 * so we never commit a user without a wallet.
 */
@Component
public class UserRegisteredWalletListener {

    private final WalletService walletService;

    public UserRegisteredWalletListener(WalletService walletService) {
        this.walletService = walletService;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserRegistered(UserRegisteredEvent event) {
        walletService.ensureWallet(event.userId());
    }
}
