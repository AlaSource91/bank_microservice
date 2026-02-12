-- V5__insert_sample_data_transactions.sql
-- Insert sample transaction data
-- Using INSERT IGNORE to skip duplicates if migration runs multiple times

INSERT IGNORE INTO bank_transaction (reference_id, source_account_id, destination_account_id, amount, description, status) VALUES
('TXN001', 1, 2, 500.00, 'Payment for services', 'COMPLETED'),
('TXN002', 2, 3, 1000.00, 'Invoice payment', 'COMPLETED'),
('TXN003', 3, 1, 250.00, 'Refund', 'COMPLETED'),
('TXN004', 4, 5, 2000.00, 'Salary payment', 'COMPLETED'),
('TXN005', 5, 7, 5000.00, 'Business transfer', 'COMPLETED'),
('TXN006', 1, 4, 100.00, 'Transfer', 'PENDING'),
('TXN007', 8, 2, 1500.00, 'Payment', 'COMPLETED');
