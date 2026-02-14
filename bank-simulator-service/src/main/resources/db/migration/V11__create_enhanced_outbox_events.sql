-- Enhanced Outbox Events table for At-least-once delivery
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL COMMENT 'Transaction ID',
    aggregate_type VARCHAR(100) NOT NULL COMMENT 'BANK_TRANSACTION',
    event_type VARCHAR(100) NOT NULL COMMENT 'TRANSACTION_COMPLETED/FAILED',
    event_payload TEXT NOT NULL COMMENT 'JSON event data',
    idempotency_key VARCHAR(500) NOT NULL UNIQUE COMMENT 'Redis key for deduplication',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    error_message VARCHAR(1000) NULL,
    status VARCHAR(100) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED/PROCESSING/DEAD',
    is_published BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Legacy compatibility flag',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    last_error VARCHAR(1000) NULL,
    next_retry_at TIMESTAMP NULL,
    version BIGINT DEFAULT 0,

    INDEX idx_outbox_unpublished (is_published, retry_count, next_retry_at),
    INDEX idx_outbox_aggregate (aggregate_id, aggregate_type),
    INDEX idx_outbox_idempotency (idempotency_key),
    INDEX idx_outbox_cleanup (is_published, published_at)
);