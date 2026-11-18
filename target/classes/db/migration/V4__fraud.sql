-- Fraud rules, evidence, assessments and admin alerts.
CREATE TABLE fraud_rules (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    code                VARCHAR(50)   NOT NULL,
    name                VARCHAR(120)  NOT NULL,
    description         VARCHAR(500)  NULL,
    category            VARCHAR(50)   NULL,
    max_points          INT           NOT NULL,
    threshold_value     DECIMAL(19,2) NULL,
    time_window_minutes INT           NULL,
    enabled             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_fraud_rules_code UNIQUE (code),
    CONSTRAINT chk_fraud_rule_points CHECK (max_points >= 0 AND max_points <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fraud_rule_hits (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT       NOT NULL,
    rule_id        BIGINT       NOT NULL,
    detail         VARCHAR(500) NOT NULL,
    points         INT          NOT NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_hit_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_hit_rule FOREIGN KEY (rule_id) REFERENCES fraud_rules (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fraud_assessments (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id           BIGINT       NOT NULL,
    score                    INT          NOT NULL,
    risk_level               VARCHAR(10)  NOT NULL,
    explanation_deterministic TEXT        NOT NULL,
    explanation_ai           TEXT         NULL,
    ai_source                VARCHAR(20)  NOT NULL DEFAULT 'MOCK',
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_assessment_transaction UNIQUE (transaction_id),
    CONSTRAINT chk_assessment_score CHECK (score >= 0 AND score <= 100),
    CONSTRAINT chk_assessment_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT fk_assessment_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE fraud_alerts (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id    BIGINT       NOT NULL,
    severity          VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    risk_score        INT          NOT NULL,
    reason_summary    TEXT         NOT NULL,
    assigned_admin_id BIGINT       NULL,
    resolution_notes  TEXT         NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at       DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_alert_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED')),
    CONSTRAINT fk_alert_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_admin FOREIGN KEY (assigned_admin_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
