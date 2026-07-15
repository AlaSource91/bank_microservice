package com.alaeldin.account_service.exception;

/**
 * Exception thrown when a user attempts to access or modify a resource
 * they don't have permission to access.
 */
public class UnauthorizedAccessException extends RuntimeException {

    /**
     * Constructs a new UnauthorizedAccessException with the specified detail message.
     *
     * @param message the detail message explaining the exception
     */
    public UnauthorizedAccessException(String message) {
        super(message);
    }

    /**
     * Constructs a new UnauthorizedAccessException with the specified detail message and cause.
     *
     * @param message the detail message explaining the exception
     * @param cause the underlying cause of this exception
     */
    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}

