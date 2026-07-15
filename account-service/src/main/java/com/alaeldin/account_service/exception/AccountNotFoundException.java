package com.alaeldin.account_service.exception;

/**
 * Exception thrown when a bank account is not found in the system.
 */
public class AccountNotFoundException extends RuntimeException {

    /**
     * Constructs a new AccountNotFoundException with the specified detail message.
     *
     * @param message the detail message explaining the exception
     */
    public AccountNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new AccountNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message explaining the exception
     * @param cause the underlying cause of this exception
     */
    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

