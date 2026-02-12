-- Add distributed locking columns to bank_account table
ALTER TABLE bank_account ADD COLUMN locked_by VARCHAR(100) NULL COMMENT 'Identifier of the process/instance that acquired the lock';

ALTER TABLE bank_account ADD COLUMN lock_timestamp DATETIME NULL COMMENT 'Timestamp when the lock was acquired. Locks expire after 5 minutes (configurable)';

-- Add index for performance when checking locks
CREATE INDEX idx_bank_account_lock ON bank_account(account_number, locked_by, lock_timestamp);
