-- Wallet and append-only ledger. Money is DECIMAL only, never floating point.
CREATE TABLE wallets (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    currency          VARCHAR(3)    NOT NULL DEFAULT 'INR',
    available_balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ledger_balance    DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    version           INT           NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_wallets_user UNIQUE (user_id),
    CONSTRAINT chk_wallets_balance_nonnegative CHECK (available_balance >= 0),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE wallet_ledger_entries (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    wallet_id      BIGINT        NOT NULL,
    transaction_id BIGINT        NULL,
    entry_type     VARCHAR(10)   NOT NULL,
    amount         DECIMAL(19,2) NOT NULL,
    balance_after  DECIMAL(19,2) NOT NULL,
    description    VARCHAR(255)  NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
