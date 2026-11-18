-- Indexes supporting history, velocity rules, admin queues and outbox polling.
CREATE INDEX idx_tx_sender_created ON transactions (sender_user_id, created_at);
CREATE INDEX idx_tx_receiver_created ON transactions (receiver_user_id, created_at);
CREATE INDEX idx_tx_status_created ON transactions (status, created_at);
CREATE INDEX idx_ledger_wallet_created ON wallet_ledger_entries (wallet_id, created_at);
CREATE INDEX idx_outbox_status_next ON outbox_events (status, next_attempt_at);
CREATE INDEX idx_alerts_status_created ON fraud_alerts (status, created_at);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, read_at);
CREATE INDEX idx_assessments_level ON fraud_assessments (risk_level);
