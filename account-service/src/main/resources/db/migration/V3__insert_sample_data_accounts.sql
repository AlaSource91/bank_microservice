-- V3__insert_sample_data_accounts.sql
-- Insert sample bank accounts data
-- Using INSERT IGNORE to skip duplicates if migration runs multiple times

INSERT IGNORE INTO accounts (account_number, account_holder_name, balance, account_type, status, user_id, version) VALUES
('AE202401001', 'Ahmed Ali', 5000.00, 'PERSONAL', 'ACTIVE', 1, 0),
('AE202401002', 'Fatima Hassan', 15000.00, 'PERSONAL', 'ACTIVE', 2, 0),
('AE202401003', 'Mohammed Ibrahim', 25000.00, 'BUSINESS', 'ACTIVE', 3, 0),
('AE202401004', 'Sara Johnson', 8500.50, 'PERSONAL', 'ACTIVE', 4, 0),
('AE202401005', 'Tech Solutions LLC', 75000.00, 'BUSINESS', 'ACTIVE', 5, 0),
('AE202401006', 'John Smith', 3000.00, 'PERSONAL', 'FROZEN', 6, 0),
('AE202401007', 'Global Trading Co', 100000.00, 'BUSINESS', 'ACTIVE', 7, 0),
('AE202401008', 'Lisa Anderson', 12000.00, 'PERSONAL', 'ACTIVE', 8, 0);
