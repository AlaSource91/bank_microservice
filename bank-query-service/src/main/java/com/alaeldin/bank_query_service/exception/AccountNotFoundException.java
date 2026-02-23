package com.alaeldin.bank_query_service.exception;

/**
 * Exception thrown when an account is not found in the read model.
 * This is a checked exception that should be handled by the controller layer.
 */
public class AccountNotFoundException extends RuntimeException {

    /**
     * Constructs a new AccountNotFoundException with the specified detail message.
     *
     * @param message the detail message explaining why the account was not found
     */
    public AccountNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new AccountNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

