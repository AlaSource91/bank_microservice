-- V1__create_bank_accounts.sql
-- Create bank_account table for storing account information

CREATE TABLE IF NOT EXISTS bank_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique account identifier',
    account_number VARCHAR(20) NOT NULL UNIQUE COMMENT 'Unique account number',
    account_holder_name VARCHAR(100) NOT NULL COMMENT 'Name of the account holder',
    balance DECIMAL(19, 2) NOT NULL COMMENT 'Current account balance',
    account_type VARCHAR(20) NOT NULL COMMENT 'Type: PERSONAL or BUSINESS',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Status: ACTIVE, FROZEN, or CLOSED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Account creation timestamp',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',

    INDEX idx_account_number (account_number),
    INDEX idx_account_holder_name (account_holder_name),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT 'Bank account master table';

-- Create bank_transaction table for storing transaction records

CREATE TABLE IF NOT EXISTS bank_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique transaction identifier',
    reference_id VARCHAR(50) NOT NULL UNIQUE COMMENT 'Unique transaction reference ID',
    source_account_id BIGINT NOT NULL COMMENT 'Source account ID',
    destination_account_id BIGINT NOT NULL COMMENT 'Destination account ID',
    amount DECIMAL(19, 2) NOT NULL COMMENT 'Transaction amount',
    description VARCHAR(500) NOT NULL COMMENT 'Transaction description',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Status: PENDING, PROCESSING, COMPLETED, FAILED, REVERSED, TIMED_OUT',
    error_code VARCHAR(50) COMMENT 'Error code if transaction failed',
    error_message VARCHAR(500) COMMENT 'Error message if transaction failed',
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Transaction execution timestamp',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',

    -- Foreign key constraints
    CONSTRAINT fk_source_account FOREIGN KEY (source_account_id)
        REFERENCES bank_account(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_destination_account FOREIGN KEY (destination_account_id)
        REFERENCES bank_account(id) ON DELETE RESTRICT ON UPDATE CASCADE,

    -- Indexes for better query performance
    INDEX idx_reference_id (reference_id),
    INDEX idx_source_account_id (source_account_id),
    INDEX idx_destination_account_id (destination_account_id),
    INDEX idx_status (status),
    INDEX idx_transaction_date (transaction_date),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT 'Bank transaction records table';
