package com.alaeldin.api_gateway.exception;

/**
 * Custom exception for unauthorized access attempts in the API Gateway.
 * 
 * <p>This exception is thrown when:</p>
 * <ul>
 *   <li>JWT token is missing or invalid</li>
 *   <li>JWT token is expired</li>
 *   <li>Token signature verification fails</li>
 *   <li>Required authentication is not provided</li>
 * </ul>
 * 
 * @author Alaeldin
 * @version 1.0
 */
public class UnauthorizedException extends RuntimeException {
    
    /**
     * Constructs a new UnauthorizedException with the specified message.
     * 
     * @param message the detail message
     */
    public UnauthorizedException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new UnauthorizedException with the specified message and cause.
     * 
     * @param message the detail message
     * @param cause the cause
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}

