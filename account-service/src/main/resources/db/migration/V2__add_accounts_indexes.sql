-- V2__add_bank_accounts_indexes.sql
-- Add additional indexes for improved query performance and data integrity

-- Additional indexes for bank_account table
ALTER TABLE  accounts
ADD UNIQUE INDEX uk_account_number (account_number);

-- Create composite index for searching accounts by holder and status
ALTER TABLE accounts
ADD INDEX idx_holder_status (account_holder_name, status);

