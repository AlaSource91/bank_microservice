package com.alaeldin.read_account_service.exception;

/**
 * Exception thrown when an account is not found in the read model
 * this is a checked exception that should be handled by the controller layer
 */
public class AccountNotFoundException  extends RuntimeException{

    /**
     * Constructs a new AccountNotFoundException with the specific detail message
     *
     * @Param message the detail message explaining why the account was NotFound
     */

    public AccountNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new AccountNotFoundException with specific details  message and cause
     */
     public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
     }
}
