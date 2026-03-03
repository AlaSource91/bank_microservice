package com.alaeldin.bank_query_service.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST API endpoints.
 * Provides consistent error responses across all controllers.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles AccountNotFoundException (404 Not Found).
     *
     * @param ex the exception
     * @return error response with 404 status
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotFound(AccountNotFoundException ex) {
        log.warn("Account not found: {}", ex.getMessage());

        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Account Not Found",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles TransactionNotFoundException (404 Not Found).
     *
     * @param ex the exception
     * @return error response with 404 status
     */
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionNotFound(TransactionNotFoundException ex) {
        log.warn("Transaction not found: {}", ex.getMessage());

        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Transaction Not Found",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles IllegalArgumentException (400 Bad Request).
     *
     * @param ex the exception
     * @return error response with 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());

        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Request",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles validation errors from @Valid and @Validated annotations.
     *
     * @param ex the exception
     * @return error response with 400 status
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles type mismatch errors (e.g., passing string when integer expected).
     *
     * @param ex the exception
     * @return error response with 400 status
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch error: {}", ex.getMessage());

        String message = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s",
                ex.getValue(),
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );

        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Type Mismatch",
                message
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles all other unexpected exceptions (500 Internal Server Error).
     *
     * @param ex the exception
     * @return error response with 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        Map<String, Object> errorResponse = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later."
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Builds a standardized error response.
     *
     * @param status  HTTP status code
     * @param error   error type/title
     * @param message detailed error message
     * @return Map containing error details
     */
    private Map<String, Object> buildErrorResponse(int status, String error, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", status);
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        return errorResponse;
    }
}

