-- V3__insert_sample_data.sql
-- Insert sample test data for development and testing purposes
-- This migration can be skipped in production environments

-- Insert sample bank accounts
-- Note: created_at and updated_at will use DEFAULT CURRENT_TIMESTAMP from table definition
INSERT INTO bank_account (account_number, account_holder_name, balance, account_type, status) VALUES
('AE202401001', 'Ahmed Ali', 5000.00, 'PERSONAL', 'ACTIVE'),
('AE202401002', 'Fatima Hassan', 15000.00, 'PERSONAL', 'ACTIVE'),
('AE202401003', 'Mohammed Ibrahim', 25000.00, 'BUSINESS', 'ACTIVE'),
('AE202401004', 'Sara Johnson', 8500.50, 'PERSONAL', 'ACTIVE'),
('AE202401005', 'Tech Solutions LLC', 75000.00, 'BUSINESS', 'ACTIVE'),
('AE202401006', 'John Smith', 3000.00, 'PERSONAL', 'FROZEN'),
('AE202401007', 'Global Trading Co', 100000.00, 'BUSINESS', 'ACTIVE'),
('AE202401008', 'Lisa Anderson', 12000.00, 'PERSONAL', 'ACTIVE');

-- Insert sample transactions
-- Note: transaction_date, created_at and updated_at will use DEFAULT CURRENT_TIMESTAMP from table definition
INSERT INTO bank_transaction (reference_id, source_account_id, destination_account_id, amount, description, status) VALUES
('TXN001', 1, 2, 500.00, 'Payment for services', 'COMPLETED'),
('TXN002', 2, 3, 1000.00, 'Invoice payment', 'COMPLETED'),
('TXN003', 3, 1, 250.00, 'Refund', 'COMPLETED'),
('TXN004', 4, 5, 2000.00, 'Salary payment', 'COMPLETED'),
('TXN005', 5, 7, 5000.00, 'Business transfer', 'COMPLETED'),
('TXN006', 1, 4, 100.00, 'Transfer', 'PENDING'),
('TXN007', 8, 2, 1500.00, 'Payment', 'COMPLETED');

