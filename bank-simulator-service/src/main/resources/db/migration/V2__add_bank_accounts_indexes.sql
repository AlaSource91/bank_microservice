-- V2__add_bank_accounts_indexes.sql
-- Add additional indexes for improved query performance and data integrity

-- Additional indexes for bank_account table
ALTER TABLE bank_account
ADD UNIQUE INDEX uk_account_number (account_number);

-- Create composite index for searching accounts by holder and status
ALTER TABLE bank_account
ADD INDEX idx_holder_status (account_holder_name, status);

-- Additional indexes for bank_transaction table
ALTER TABLE bank_transaction
ADD UNIQUE INDEX uk_reference_id (reference_id);

-- Create composite index for transaction searches
ALTER TABLE bank_transaction
ADD INDEX idx_source_status (source_account_id, status);

-- Create composite index for destination transactions
ALTER TABLE bank_transaction
ADD INDEX idx_destination_status (destination_account_id, status);

-- Create index for transaction date range queries
ALTER TABLE bank_transaction
ADD INDEX idx_transaction_date_status (transaction_date, status);
