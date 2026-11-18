-- Reference data. Roles are inserted here; the registration service only ever assigns CUSTOMER.
INSERT INTO roles (name) VALUES ('CUSTOMER'), ('ADMIN');

-- Configurable fraud rules. Weights sum to 100; thresholds and windows can be edited by an admin.
INSERT INTO fraud_rules (code, name, description, category, max_points, threshold_value, time_window_minutes, enabled) VALUES
('AMOUNT_DEVIATION', 'Amount deviation',
 'Amount above the configured multiple of the user historical average or above the absolute cap.',
 'BEHAVIOUR', 25, 3.00, 43200, TRUE),
('VELOCITY', 'Transaction velocity',
 'More simulated transactions than allowed within the configured time window.',
 'VELOCITY', 20, 5.00, 5, TRUE),
('REPEATED_TRANSACTION', 'Repeated transaction',
 'Same receiver and same amount repeated within the configured time window.',
 'VELOCITY', 15, 2.00, 10, TRUE),
('UNFAMILIAR_DEVICE', 'Unfamiliar device',
 'Device fingerprint has not been seen before or is not trusted for this user.',
 'DEVICE', 15, NULL, NULL, TRUE),
('UNUSUAL_LOCATION', 'Unusual location',
 'Location hint differs from the user recent locations.',
 'LOCATION', 10, NULL, NULL, TRUE),
('FAILED_PATTERN', 'Failed transaction pattern',
 'Number of failed attempts within the configured time window is above the limit.',
 'HISTORY', 15, 3.00, 15, TRUE);
