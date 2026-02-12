package com.alaeldin.bank_simulator_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested resource cannot be found.
 * This is a not found exception (HTTP 404) typically used when querying for resources
 * that don't exist in the database or system.
 *
 * <p>This exception is automatically caught by the GlobalExceptionHandler which returns a standardized
 * API error response with HTTP status 404 NOT_FOUND.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * throw new ResourceNotFoundException("BankAccount", "accountNumber", "ACC123456");
 * // Results in message: "BankAccount not found with accountNumber: 'ACC123456'"
 * </pre>
 *
 * @see com.alaeldin.bank_simulator_service.exception.GlobalExceptionHandler
 */
@Getter
@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    /**
     * The name of the resource type that was not found (e.g., "BankAccount", "User").
     * Used to provide context about what type of resource is missing.
     */
    private final String resourceName;

    /**
     * The name of the field used for the search query (e.g., "accountNumber", "id", "email").
     * Helps identify which field was used in the lookup.
     */
    private final String fieldName;

    /**
     * The value of the field that was searched for (e.g., "ACC123456", "42", "user@example.com").
     * Provides the specific value that didn't match any resource.
     */
    private final Object fieldValue;

    /**
     * Constructs a ResourceNotFoundException with detailed context about the missing resource.
     *
     * @param resourceName the name of the resource type that was not found (e.g., "BankAccount")
     * @param fieldName    the name of the field used for the search query (e.g., "accountNumber")
     * @param fieldValue   the value of the field that was searched for (e.g., "ACC123456")
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }
}
