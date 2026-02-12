-- V7__add_completed_at_failed_at_to_bank_transaction.sql
-- Add missing completed_at and failed_at columns to bank_transaction table

ALTER TABLE bank_transaction
ADD COLUMN completed_at TIMESTAMP NULL COMMENT 'Transaction completion timestamp',
ADD COLUMN failed_at TIMESTAMP NULL COMMENT 'Transaction failure timestamp';
