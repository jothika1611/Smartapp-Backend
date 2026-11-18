package com.smartpay.wallet.dto;

/** Both wallet views after one atomic transfer. Contains DTOs only, never JPA entities. */
public class WalletTransferResult {

    private final WalletResponse senderWallet;
    private final WalletResponse receiverWallet;

    public WalletTransferResult(WalletResponse senderWallet, WalletResponse receiverWallet) {
        this.senderWallet = senderWallet;
        this.receiverWallet = receiverWallet;
    }

    public WalletResponse getSenderWallet() { return senderWallet; }
    public WalletResponse getReceiverWallet() { return receiverWallet; }
}
