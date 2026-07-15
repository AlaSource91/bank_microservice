-- V1__create_accounts.sql
-- Create account table for storing account information

CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique account identifier',
    account_number VARCHAR(20) NOT NULL UNIQUE COMMENT 'Unique account number',
    account_holder_name VARCHAR(100) NOT NULL COMMENT 'Name of the account holder',
    balance DECIMAL(19, 2) NOT NULL COMMENT 'Current account balance',
    account_type VARCHAR(20) NOT NULL COMMENT 'Type: PERSONAL or BUSINESS',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE, FROZEN, or CLOSED',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    locked_by VARCHAR(20) NULL COMMENT 'Identifier of entity holding the lock',
    lock_timestamp TIMESTAMP NULL COMMENT 'Timestamp when lock was acquired',
    user_id BIGINT NOT NULL COMMENT 'User ID from Auth service',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Account creation timestamp',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',

    INDEX idx_account_number (account_number),
    INDEX idx_account_holder_name (account_holder_name),
    INDEX idx_status (status),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT 'Bank accounts master table';
