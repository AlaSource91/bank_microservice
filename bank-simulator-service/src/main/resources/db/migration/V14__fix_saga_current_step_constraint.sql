-- V14__fix_saga_current_step_constraint.sql
-- Fix: chk_saga_current_step constraint listed values that don't match the
-- SagaStep enum names used by the application:
--
--   Enum value        Old constraint value (wrong)
--   ----------------  ----------------------------
--   INIT              (missing)
--   DEBIT_SOURCE      DEBIT_ACCOUNT
--   CREDIT_DESTINATION CREDIT_ACCOUNT
--   COMPENSATE_DEBIT  COMPENSATE_DEBIT  (correct)
--   COMPENSATE_CREDIT (missing)
--   COMPLETE          COMPLETE          (correct)
--   SAGA_FAILED       (missing)
--
-- Drop the old constraint and recreate it with all current SagaStep enum values.

ALTER TABLE saga_state
    DROP CONSTRAINT chk_saga_current_step;

ALTER TABLE saga_state
    ADD CONSTRAINT chk_saga_current_step CHECK (
        current_step IN (
            'INIT',              -- SagaStep.INIT
            'DEBIT_SOURCE',      -- SagaStep.DEBIT_SOURCE
            'CREDIT_DESTINATION',-- SagaStep.CREDIT_DESTINATION
            'COMPENSATE_DEBIT',  -- SagaStep.COMPENSATE_DEBIT
            'COMPENSATE_CREDIT', -- SagaStep.COMPENSATE_CREDIT
            'COMPLETE',          -- SagaStep.COMPLETE
            'SAGA_FAILED'        -- SagaStep.SAGA_FAILED
        )
    );

