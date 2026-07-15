package com.alaeldin.Auth_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── Validation ──────────────────────────────────────────────────────────

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] Unsupported media type: {} - Path: {}", ex.getContentType(), request.getRequestURI());

        String message;
        Map<String, String> details = new HashMap<>();

        // Special guidance for the register endpoint
        if (request.getRequestURI().contains("/register")) {
            message = "Invalid multipart request format. The /register endpoint requires multipart/form-data with TWO parts: " +
                      "(1) 'json' part with Content-Type: application/json containing RegisterRequest data, " +
                      "(2) 'file' part containing the identity document.";

            details.put("error", "The 'json' part must have Content-Type: application/json, not " + ex.getContentType());
            details.put("howToFix_Postman", "In Postman: Body → form-data → Add 'json' key (Text) → Hover over key → Click ⚙️ → Set Content-Type to 'application/json'");
            details.put("howToFix_cURL", "Use: -F \"json={...};type=application/json\" -F \"file=@path/to/file\"");
            details.put("documentation", "See REGISTER_ENDPOINT_GUIDE.md or REGISTER_QUICK_FIX.md for detailed examples");
            details.put("supportedTypes", "The 'json' part must be application/json; the 'file' part can be any file type");
        } else {
            message = "Unsupported Content-Type: " + ex.getContentType() +
                      ". Supported types: " + ex.getSupportedMediaTypes();
        }

        ApiErrorResponse response = buildErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Media Type",
                message,
                details,
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        log.warn("[GlobalExceptionHandler] Validation failed for {} field(s): {}", fieldErrors.size(), fieldErrors);

        ApiErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "Request validation failed for " + fieldErrors.size() + " field(s)",
                fieldErrors,
                request.getRequestURI());

        return ResponseEntity.status(response.getStatus()).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage));

        log.warn("[GlobalExceptionHandler] Constraint violation: {}", violations);

        ApiErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "Constraint violation occurred",
                violations,
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ─── Authentication & Authorisation ──────────────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] Invalid credentials: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication Failed", ex, request);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenExpired(
            TokenExpiredException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] Token expired: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Token Expired", ex, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Access Denied", ex, request);
    }

    // ─── Resource Not Found ───────────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UserNotFoundException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] User not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "User Not Found", ex, request);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRoleNotFound(
            RoleNotFoundException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] Role not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Role Not Found", ex, request);
    }

    @ExceptionHandler(RefreshTokenNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshTokenNotFound(
            RefreshTokenNotFoundException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] Refresh token not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Refresh Token Not Found", ex, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Resource Not Found", ex, request);
    }

    // ─── Conflict ─────────────────────────────────────────────────────────────

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] User already exists: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "User Already Exists", ex, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn("[GlobalExceptionHandler] Data integrity violation: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Data Integrity Violation", ex, request);
    }

    // ─── Fallback ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("[GlobalExceptionHandler] Unexpected {} error: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        ApiErrorResponse response = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                null,
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String error, Exception ex, HttpServletRequest request) {

        ApiErrorResponse response = buildErrorResponse(status, error, ex.getMessage(), null, request.getRequestURI());
        return ResponseEntity.status(status).body(response);
    }

    private ApiErrorResponse buildErrorResponse(
            HttpStatus status, String error, String message,
            Map<String, String> details, String path) {

        return ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .details(details)
                .path(path)
                .build();
    }
}
