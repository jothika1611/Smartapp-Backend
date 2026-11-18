-- Simulated transactions. Unique (sender, idempotency key) prevents duplicate transfers.
CREATE TABLE transactions (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    reference          VARCHAR(40)   NOT NULL,
    sender_user_id     BIGINT        NOT NULL,
    receiver_user_id   BIGINT        NOT NULL,
    type               VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    amount             DECIMAL(19,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL DEFAULT 'INR',
    idempotency_key    VARCHAR(100)  NOT NULL,
    device_fingerprint VARCHAR(100)  NULL,
    location_hint      VARCHAR(100)  NULL,
    description        VARCHAR(255)  NULL,
    risk_score         INT           NOT NULL DEFAULT 0,
    risk_level         VARCHAR(10)   NOT NULL DEFAULT 'LOW',
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    processed_at       DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transactions_reference UNIQUE (reference),
    CONSTRAINT uk_transactions_idempotency UNIQUE (sender_user_id, idempotency_key),
    CONSTRAINT chk_tx_type CHECK (type IN ('PAYMENT', 'TRANSFER', 'FUNDING')),
    CONSTRAINT chk_tx_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'HOLD', 'REJECTED')),
    CONSTRAINT chk_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_tx_risk_score CHECK (risk_score >= 0 AND risk_score <= 100),
    CONSTRAINT chk_tx_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT fk_tx_sender FOREIGN KEY (sender_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_receiver FOREIGN KEY (receiver_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE wallet_ledger_entries
    ADD CONSTRAINT fk_ledger_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT;
