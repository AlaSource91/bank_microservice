package com.alaeldin.bank_simulator_service.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard API error response object used for error handling and reporting.
 * This class provides a consistent structure for all error responses returned by the API.
 *
 * The response can be serialized to JSON with non-null fields only, making it clean and focused.
 * Supports detailed error information including error codes, status codes, and additional details.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    /**
     * The timestamp when the error occurred.
     * Recorded in UTC timezone.
     */
    private LocalDateTime timestamp;

    /**
     * A human-readable error message describing the error.
     * Provides context about what went wrong.
     */
    private String message;

    /**
     * The request path that caused the error.
     * Useful for debugging and identifying which endpoint failed.
     */
    private String path;

    /**
     * A unique error code identifier for the error.
     * Allows clients to programmatically identify and handle specific error types.
     * Example: "ACCOUNT_NOT_FOUND", "INSUFFICIENT_BALANCE", etc.
     */
    private String errorCode;

    /**
     * The HTTP status code associated with the error.
     * Standard HTTP status codes (400, 404, 500, etc.).
     */
    private int statusCode;

    /**
     * Additional error details in a key-value map format.
     * Used for validation errors or other scenarios requiring detailed error information.
     * May be null if no additional details are available.
     */
    private Map<String, String> details;

    /**
     * Constructor for creating an ApiErrorResponse with basic error information.
     * Creates a response without additional details map.
     *
     * @param timestamp the time when the error occurred
     * @param message   a descriptive error message
     * @param path      the request path that caused the error
     * @param errorCode a unique error code identifier
     * @param statusCode the HTTP status code
     */
    public ApiErrorResponse(LocalDateTime timestamp, String message, String path,
                            String errorCode, int statusCode) {
        this.timestamp = timestamp;
        this.message = message;
        this.path = path;
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }
}
