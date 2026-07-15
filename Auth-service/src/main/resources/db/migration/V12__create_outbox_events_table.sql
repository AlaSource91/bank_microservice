-- ============================================================
-- V11 – Create the transactional outbox table
-- Used by the Outbox Pattern to guarantee at-least-once
-- delivery of domain events to Kafka.
-- ============================================================

CREATE TABLE IF NOT EXISTS outbox_events
(
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    aggregate_id    VARCHAR(255)    NOT NULL,
    aggregate_type  VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    event_payload   TEXT            NOT NULL,
    idempotency_key VARCHAR(255)    NOT NULL,
    created_at      DATETIME(6)     NOT NULL,
    published_at    DATETIME(6)     NULL,
    status          ENUM(
                        'PENDING',
                        'PROCESSING',
                        'SENT',
                        'FAILED',
                        'DEAD'
                    )               NOT NULL DEFAULT 'PENDING',
    retry_count     INT             NOT NULL DEFAULT 0,
    max_retries     INT             NOT NULL DEFAULT 3,
    next_retry_at   DATETIME(6)     NULL,
    error_message   TEXT            NULL,
    version         BIGINT          NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_idempotency_key (idempotency_key),
    INDEX idx_outbox_status_next_retry (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

