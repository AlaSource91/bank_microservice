package com.alaeldin.bank_simulator_service.exception;

/**
 * Exception thrown when attempting to acquire a lock on an account that is already locked.
 * This typically occurs during concurrent transaction attempts on the same account.
 */
public class AccountLockedException extends RuntimeException {

    private final String accountNumber;
    private final String lockedBy;

    /**
     * Constructs a new AccountLockedException.
     *
     * @param accountNumber the account number that is locked
     * @param lockedBy the identifier of the process that holds the lock
     */
    public AccountLockedException(String accountNumber, String lockedBy) {
        super(String.format("Account %s is already locked by %s", accountNumber, lockedBy));
        this.accountNumber = accountNumber;
        this.lockedBy = lockedBy;
    }

    /**
     * Constructs a new AccountLockedException with a custom message.
     *
     * @param message the custom error message
     */
    public AccountLockedException(String message) {
        super(message);
        this.accountNumber = null;
        this.lockedBy = null;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getLockedBy() {
        return lockedBy;
    }
}
