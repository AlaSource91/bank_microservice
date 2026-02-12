CREATE TABLE transaction_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ledger_entry_id VARCHAR(50) UNIQUE NOT NULL,
    transaction_reference VARCHAR(50) NOT NULL,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_before DECIMAL(19, 2),
    balance_after DECIMAL(19, 2),
    entry_date TIMESTAMP NOT NULL,
    description VARCHAR(500),

    FOREIGN KEY (account_id) REFERENCES bank_account(id),
    INDEX idx_account_date (account_id, entry_date),
    INDEX idx_transaction (transaction_reference)
);