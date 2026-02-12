package com.alaeldin.bank_simulator_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.naming.AuthenticationException;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for the entire application.
 * This class centralizes exception handling across all REST controllers using the @RestControllerAdvice annotation.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Consistent API error responses for all exceptions</li>
 *   <li>Proper HTTP status codes based on exception type</li>
 *   <li>Comprehensive logging for debugging and monitoring</li>
 *   <li>Security-aware error messages to prevent information leakage</li>
 *   <li>Detailed validation error information for clients</li>
 * </ul>
 *
 * <p>Exception handlers are organized by HTTP status code for clarity:</p>
 * <ul>
 *   <li>400 Bad Request: Validation, constraint violations, missing parameters</li>
 *   <li>401 Unauthorized: Authentication failures, invalid credentials</li>
 *   <li>403 Forbidden: Access denied exceptions</li>
 *   <li>404 Not Found: Resource not found exceptions</li>
 *   <li>405 Method Not Allowed: Unsupported HTTP methods</li>
 *   <li>409 Conflict: Business logic conflicts (e.g., duplicate data)</li>
 *   <li>415 Unsupported Media Type: Invalid content types</li>
 *   <li>500 Internal Server Error: Unexpected/uncaught exceptions</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 400 BAD REQUEST HANDLERS ====================

    /**
     * Handles MethodArgumentNotValidException (400 BAD REQUEST).
     * Triggered when request body or parameter validation fails.
     *
     * @param ex      the MethodArgumentNotValidException containing validation errors
     * @param request the HTTP request
     * @return ResponseEntity with field-level error details and 400 status
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Validation failed for request: {} - Path: {}",
                ex.getBindingResult().getObjectName(), request.getRequestURI());

        // Extract field errors
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
            log.debug("Field validation error - Field: {}, Message: {}", fieldName, errorMessage);
        });

        String errorMessage = String.format("Validation failed for %d field(s)", fieldErrors.size());

        ApiErrorResponse error = buildErrorResponse(
                errorMessage,
                request.getRequestURI(),
                "VALIDATION_ERROR",
                HttpStatus.BAD_REQUEST
        );

        // Add field errors as details for client consumption
        error.setDetails(fieldErrors);

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ConstraintViolationException (400 BAD REQUEST).
     * Triggered when constraint validation fails (e.g., @NotNull, @Size, etc.).
     *
     * @param ex      the ConstraintViolationException
     * @param request the HTTP request
     * @return ResponseEntity with constraint violation details and 400 status
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.warn("Constraint violation: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));

        ApiErrorResponse error = buildErrorResponse(
                "Constraint violation occurred",
                request.getRequestURI(),
                "CONSTRAINT_VIOLATION",
                HttpStatus.BAD_REQUEST
        );
        error.setDetails(violations);

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles MissingServletRequestParameterException (400 BAD REQUEST).
     * Triggered when a required request parameter is missing.
     *
     * @param ex      the MissingServletRequestParameterException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 400 status
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing required parameter: {} - Path: {}",
                ex.getParameterName(), request.getRequestURI());

        String message = String.format("Required parameter '%s' of type '%s' is missing",
                ex.getParameterName(), ex.getParameterType());

        ApiErrorResponse error = buildErrorResponse(
                message,
                request.getRequestURI(),
                "MISSING_PARAMETER",
                HttpStatus.BAD_REQUEST
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // ==================== 401 UNAUTHORIZED HANDLERS ====================

    /**
     * Handles AuthenticationException (401 UNAUTHORIZED).
     * Triggered when authentication fails (invalid credentials, missing auth, etc.).
     *
     * @param ex      the AuthenticationException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 401 status
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {

        log.info("Authentication failed - Path: {} - Reason: {}",
                request.getRequestURI(), ex.getMessage());

        ApiErrorResponse error = buildErrorResponse(
                "Authentication failed. Please check your credentials.",
                request.getRequestURI(),
                "AUTHENTICATION_FAILED",
                HttpStatus.UNAUTHORIZED
        );

        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }


    // ==================== 403 FORBIDDEN HANDLERS ====================

    /**
     * Handles AccessDeniedException (403 FORBIDDEN).
     * Triggered when an authenticated user lacks sufficient permissions.
     *
     * @param ex      the AccessDeniedException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 403 status
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied - Path: {} - Reason: {}",
                request.getRequestURI(), ex.getMessage());

        ApiErrorResponse error = buildErrorResponse(
                "You don't have permission to access this resource.",
                request.getRequestURI(),
                "ACCESS_DENIED",
                HttpStatus.FORBIDDEN
        );

        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // ==================== 404 NOT FOUND HANDLERS ====================

    /**
     * Handles ResourceNotFoundException (404 NOT FOUND).
     * Triggered when a requested resource cannot be found.
     *
     * @param ex      the ResourceNotFoundException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 404 status
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Resource not found: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ApiErrorResponse error = buildErrorResponse(
                ex.getMessage(),
                request.getRequestURI(),
                "RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );

        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    // ==================== 405 METHOD NOT ALLOWED HANDLERS ====================

    /**
     * Handles HttpRequestMethodNotSupportedException (405 METHOD NOT ALLOWED).
     * Triggered when request uses unsupported HTTP method (e.g., POST instead of GET).
     *
     * @param ex      the HttpRequestMethodNotSupportedException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 405 status
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        log.warn("HTTP method not supported: {} - Path: {} - Supported: {}",
                ex.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods());

        String message = String.format("HTTP method '%s' is not supported for this endpoint. Supported methods: %s",
                ex.getMethod(), ex.getSupportedHttpMethods());

        ApiErrorResponse error = buildErrorResponse(
                message,
                request.getRequestURI(),
                "METHOD_NOT_ALLOWED",
                HttpStatus.METHOD_NOT_ALLOWED
        );

        return new ResponseEntity<>(error, HttpStatus.METHOD_NOT_ALLOWED);
    }

    // ==================== 409 CONFLICT HANDLERS ====================

    /**
     * Handles AccountHolderNameAlreadyExist (409 CONFLICT).
     * Triggered when attempting to create an account with a duplicate holder name.
     *
     * @param ex      the AccountHolderNameAlreadyExist exception
     * @param request the HTTP request
     * @return ResponseEntity with error details and 409 status
     */
    @ExceptionHandler(AccountHolderNameAlreadyExist.class)
    public ResponseEntity<ApiErrorResponse> handleAccountHolderNameAlreadyExist(
            AccountHolderNameAlreadyExist ex,
            HttpServletRequest request) {

        log.warn("Account holder name already exists: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ApiErrorResponse error = buildErrorResponse(
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_HOLDER_DUPLICATE",
                HttpStatus.CONFLICT
        );

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Handles AccountLockedException (409 CONFLICT).
     * Triggered when attempting to perform an operation on a locked account.
     *
     * @param ex      the AccountLockedException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 409 status
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountLocked(
            AccountLockedException ex,
            HttpServletRequest request) {

        log.warn("Account locked: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        Map<String, String> details = new HashMap<>();
        if (ex.getAccountNumber() != null) {
            details.put("accountNumber", ex.getAccountNumber());
        }
        if (ex.getLockedBy() != null) {
            details.put("lockedBy", ex.getLockedBy());
        }

        ApiErrorResponse error = buildErrorResponse(
                ex.getMessage(),
                request.getRequestURI(),
                "ACCOUNT_LOCKED",
                HttpStatus.CONFLICT
        );
        error.setDetails(details);

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Handles ConcurrentModificationException (409 CONFLICT).
     * Triggered when optimistic locking detects concurrent modification.
     *
     * @param ex      the ConcurrentModificationException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 409 status
     */
    @ExceptionHandler(ConcurrentModificationException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentModification(
            ConcurrentModificationException ex,
            HttpServletRequest request) {

        log.warn("Concurrent modification detected: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        Map<String, String> details = new HashMap<>();
        if (ex.getResourceName() != null) {
            details.put("resourceName", ex.getResourceName());
        }
        if (ex.getResourceId() != null) {
            details.put("resourceId", ex.getResourceId());
        }
        if (ex.getExpectedVersion() != null) {
            details.put("expectedVersion", ex.getExpectedVersion().toString());
        }
        if (ex.getActualVersion() != null) {
            details.put("actualVersion", ex.getActualVersion().toString());
        }

        ApiErrorResponse error = buildErrorResponse(
                "The resource was modified by another transaction. Please retry your operation.",
                request.getRequestURI(),
                "CONCURRENT_MODIFICATION",
                HttpStatus.CONFLICT
        );
        error.setDetails(details);

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Handles OptimisticLockingFailureException (409 CONFLICT).
     * Triggered when JPA optimistic locking detects a version conflict.
     *
     * @param ex      the OptimisticLockingFailureException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 409 status
     */
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(
            org.springframework.dao.OptimisticLockingFailureException ex,
            HttpServletRequest request) {

        log.warn("Optimistic locking failure: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ApiErrorResponse error = buildErrorResponse(
                "The resource was modified by another transaction. Please retry your operation.",
                request.getRequestURI(),
                "OPTIMISTIC_LOCK_FAILURE",
                HttpStatus.CONFLICT
        );

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    // ==================== 415 UNSUPPORTED MEDIA TYPE HANDLERS ====================

    /**
     * Handles HttpMediaTypeNotSupportedException (415 UNSUPPORTED MEDIA TYPE).
     * Triggered when request Content-Type is not supported (e.g., XML instead of JSON).
     *
     * @param ex      the HttpMediaTypeNotSupportedException
     * @param request the HTTP request
     * @return ResponseEntity with error details and 415 status
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        log.warn("Media type not supported: {} - Path: {} - Supported: {}",
                ex.getContentType(), request.getRequestURI(), ex.getSupportedMediaTypes());

        String message = String.format("Media type '%s' is not supported. Supported types: %s",
                ex.getContentType(), ex.getSupportedMediaTypes());

        ApiErrorResponse error = buildErrorResponse(
                message,
                request.getRequestURI(),
                "UNSUPPORTED_MEDIA_TYPE",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE
        );

        return new ResponseEntity<>(error, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // ==================== 500 INTERNAL SERVER ERROR HANDLERS ====================

    /**
     * Handles all uncaught exceptions (500 INTERNAL SERVER ERROR).
     * This is the fallback handler for any exception not specifically handled above.
     *
     * <p><strong>Security Note:</strong> Returns a generic error message to clients to avoid
     * information leakage. Full exception details are logged server-side for debugging.</p>
     *
     * @param ex      the Exception
     * @param request the HTTP request
     * @return ResponseEntity with generic error message and 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGlobalException(
            Exception ex,
            HttpServletRequest request) {

        // Log full exception details server-side (including stack trace)
        log.error("Unexpected error occurred - Path: {} - Exception: {}",
                request.getRequestURI(), ex.getClass().getName(), ex);

        // Return generic message to client (avoid information leakage)
        ApiErrorResponse error = buildErrorResponse(
                "An unexpected error occurred. Please contact support if the problem persists.",
                request.getRequestURI(),
                "INTERNAL_SERVER_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR
        );

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Builds a standardized ApiErrorResponse object.
     * This helper method ensures consistency across all error responses.
     *
     * @param message    the human-readable error message
     * @param path       the request path that caused the error
     * @param errorCode  the unique error code identifier
     * @param status     the HTTP status
     * @return a new ApiErrorResponse object populated with the provided values
     */
    private ApiErrorResponse buildErrorResponse(
            String message,
            String path,
            String errorCode,
            HttpStatus status) {

        return ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .message(message)
                .path(path)
                .errorCode(errorCode)
                .statusCode(status.value())
                .build();
    }
}

