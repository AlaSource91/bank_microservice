ALTER TABLE bank_account ADD COLUMN version BIGINT NULL COMMENT 'Optimistic locking version number';

UPDATE bank_account SET version = 0 WHERE version IS NULL;

ALTER TABLE bank_account MODIFY version BIGINT NOT NULL;

-- ADD INDEX FOR Performance
CREATE INDEX idx_bank_account_version ON bank_account(account_number, version);


