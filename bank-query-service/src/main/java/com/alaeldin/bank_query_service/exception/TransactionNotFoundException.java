package com.alaeldin.bank_query_service.exception;

/**
 * Exception thrown when a transaction is not found in the read model.
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String message) {
        super(message);
    }

    public TransactionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

