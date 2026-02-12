-- V6__create_transaction_idempotency.sql
-- Create transaction_idempotency table for idempotency key management
-- This table prevents duplicate transaction processing by tracking idempotency keys

CREATE TABLE IF NOT EXISTS transaction_idempotency (
    idempotency_key VARCHAR(255) NOT NULL PRIMARY KEY COMMENT 'Unique idempotency key (UUID format)',
    transaction_id BIGINT COMMENT 'Reference to the associated bank transaction',
    request_hash VARCHAR(255) NOT NULL COMMENT 'SHA-256 hash of the request body for validation',
    status VARCHAR(20) NOT NULL COMMENT 'Status: IN_PROGRESS, COMPLETED, or FAILED',
    response_data JSON COMMENT 'Cached response data for completed transactions',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation timestamp',
    completed_at TIMESTAMP NULL COMMENT 'Transaction completion timestamp',
    expires_at TIMESTAMP NOT NULL COMMENT 'Expiration timestamp (24 hours from creation)',

    -- Foreign key constraint to bank_transaction table
    CONSTRAINT fk_transaction_idempotency_transaction FOREIGN KEY (transaction_id)
        REFERENCES bank_transaction(id) ON DELETE SET NULL ON UPDATE CASCADE,

    -- Indexes for better query performance
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_expires_at (expires_at),
    INDEX idx_transaction_id (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT 'Idempotency key tracking table for preventing duplicate transaction processing';
