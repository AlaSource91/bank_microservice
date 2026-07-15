package com.alaeldin.api_gateway.exception;

/**
 * Exception thrown when a client exceeds the rate limit.
 * 
 * @author Alaeldin
 * @version 1.0
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}

