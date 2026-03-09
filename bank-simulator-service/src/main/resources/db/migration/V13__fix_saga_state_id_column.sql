-- V13__fix_saga_state_id_column.sql
-- Fix: saga_id column was CHAR(36) which only fits a bare UUID (36 chars).
-- The application generates IDs in the format "SAGA-<referenceId>" e.g.
-- "SAGA-BANK_REF_f4baa6fb-6cb1-4c7a-a156-0f39992444a1" (up to ~50 chars).
-- Widen to VARCHAR(50) to match the @Column(length = 50) on the entity.

ALTER TABLE saga_state
    MODIFY COLUMN saga_id VARCHAR(50) NOT NULL COMMENT 'Saga instance identifier in format SAGA-<referenceId>';

