-- V12__create_saga_state.sql
-- Tracks the state of each Saga (distributed P2P transfer) for the Saga Pattern implementation.
-- Each row represents one saga instance, tied 1-to-1 with a bank_transaction record.

CREATE TABLE IF NOT EXISTS saga_state (
    -- -------------------------------------------------------------------------
    -- Identity
    -- -------------------------------------------------------------------------
    saga_id                    CHAR(36)       NOT NULL                     COMMENT 'UUID identifying this saga instance',
    transaction_reference_id   VARCHAR(50)    NOT NULL                     COMMENT 'FK → bank_transaction.reference_id',

    -- -------------------------------------------------------------------------
    -- State machine
    -- -------------------------------------------------------------------------
    current_step               VARCHAR(50)    NOT NULL                     COMMENT 'Current saga step: must match com.alaeldin.bank_simulator_service.constant.SagaStep',
    status                     VARCHAR(50)    NOT NULL DEFAULT 'PENDING'   COMMENT 'Lifecycle status — must match com.alaeldin.bank_simulator_service.constant.SagaStatus',

    -- -------------------------------------------------------------------------
    -- Transfer details (denormalised for quick access during compensation)
    -- -------------------------------------------------------------------------
    source_account_number      VARCHAR(20)    NOT NULL                     COMMENT 'Debit account number',
    destination_account_number VARCHAR(20)    NOT NULL                     COMMENT 'Credit account number',
    amount                     DECIMAL(19, 2) NOT NULL                     COMMENT 'Transfer amount (must be > 0)',

    -- -------------------------------------------------------------------------
    -- Error / retry tracking
    -- -------------------------------------------------------------------------
    failure_reason             VARCHAR(500)   NULL                         COMMENT 'Human-readable reason for failure or compensation',
    retry_count                INT            NOT NULL DEFAULT 0           COMMENT 'Number of retry attempts so far',
    max_retries                INT            NOT NULL DEFAULT 3           COMMENT 'Maximum allowed retries before marking FAILED',

    -- -------------------------------------------------------------------------
    -- Timestamps
    -- -------------------------------------------------------------------------
    started_at                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP                    COMMENT 'When the saga was created',
    updated_at                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last state change timestamp',
    completed_at               TIMESTAMP      NULL                         COMMENT 'When the saga reached a terminal state (COMPLETED / FAILED / COMPENSATED)',

    -- -------------------------------------------------------------------------
    -- Optimistic locking
    -- -------------------------------------------------------------------------
    version                    INT            NOT NULL DEFAULT 0           COMMENT 'Optimistic lock version counter',

    -- =========================================================================
    -- Primary key
    -- =========================================================================
    PRIMARY KEY (saga_id),

    -- =========================================================================
    -- Unique constraints
    -- =========================================================================
    CONSTRAINT uq_saga_transaction_ref   UNIQUE (transaction_reference_id),

    -- =========================================================================
    -- Foreign keys
    -- =========================================================================
    CONSTRAINT fk_saga_transaction
        FOREIGN KEY (transaction_reference_id)
        REFERENCES bank_transaction (reference_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    -- =========================================================================
    -- Check constraints
    -- =========================================================================
    CONSTRAINT chk_saga_amount      CHECK (amount > 0),
    CONSTRAINT chk_saga_retry       CHECK (retry_count >= 0),
    CONSTRAINT chk_saga_max_retries CHECK (max_retries > 0),
    -- Values must stay in sync with: com.alaeldin.bank_simulator_service.constant.SagaStatus
    CONSTRAINT chk_saga_status      CHECK (
        status IN (
            'PENDING',          -- SagaStatus.PENDING
            'COMPLETED',        -- SagaStatus.COMPLETED
            'FAILED',           -- SagaStatus.FAILED
            'COMPENSATING',     -- SagaStatus.COMPENSATING
            'COMPENSATED',      -- SagaStatus.COMPENSATED
            'DEBIT_COMPLETED',  -- SagaStatus.DEBIT_COMPLETED
            'CREDIT_COMPLETED'  -- SagaStatus.CREDIT_COMPLETED
        )
    ),
    CONSTRAINT chk_saga_current_step CHECK (
        current_step IN (
            'DEBIT_ACCOUNT',
            'CREDIT_ACCOUNT',
            'COMPLETE',
            'COMPENSATE_DEBIT'
        )
    ),

    -- =========================================================================
    -- Indexes
    -- =========================================================================
    INDEX idx_saga_transaction_ref  (transaction_reference_id),
    INDEX idx_saga_status           (status),
    INDEX idx_saga_source_account   (source_account_number),
    INDEX idx_saga_started_at       (started_at),
    INDEX idx_saga_updated_at       (updated_at),
    INDEX idx_saga_status_updated   (status, updated_at)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Saga Pattern state tracking for distributed P2P transfers';
