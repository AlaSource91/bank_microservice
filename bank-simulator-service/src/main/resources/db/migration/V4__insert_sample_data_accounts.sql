-- V4__insert_sample_data_accounts.sql
-- Insert sample bank accounts data
-- Using INSERT IGNORE to skip duplicates if migration runs multiple times

INSERT IGNORE INTO bank_account (account_number, account_holder_name, balance, account_type, status) VALUES
('AE202401001', 'Ahmed Ali', 5000.00, 'PERSONAL', 'ACTIVE'),
('AE202401002', 'Fatima Hassan', 15000.00, 'PERSONAL', 'ACTIVE'),
('AE202401003', 'Mohammed Ibrahim', 25000.00, 'BUSINESS', 'ACTIVE'),
('AE202401004', 'Sara Johnson', 8500.50, 'PERSONAL', 'ACTIVE'),
('AE202401005', 'Tech Solutions LLC', 75000.00, 'BUSINESS', 'ACTIVE'),
('AE202401006', 'John Smith', 3000.00, 'PERSONAL', 'FROZEN'),
('AE202401007', 'Global Trading Co', 100000.00, 'BUSINESS', 'ACTIVE'),
('AE202401008', 'Lisa Anderson', 12000.00, 'PERSONAL', 'ACTIVE');
