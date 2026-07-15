package com.alaeldin.read_account_service.exception;

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

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

/**
 * Handles AccountNotFoundException
 *
 * @param ex the Exception
 * @return error response  with 404 status
 */
 @ExceptionHandler(AccountNotFoundException.class)
 public ResponseEntity<?> handleAccountNotFoundException(AccountNotFoundException ex){

     log.warn("Account not found:{}" , ex.getMessage());

     Map<String,Object> errorResponse = buildErrorResponse(
             HttpStatus.NOT_FOUND.value(),
             "Account Not Found",
             ex.getMessage()
     );

     return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
 }

    /**
     *Handles IllegalArgumentException(400 Bad Request)
     *
     * @param ex the Exception
     * @return error response with 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex){

        log.warn("InValid Argument:{}" , ex.getMessage());
        Map<String,Object> errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Argument",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     *Handles Validation errors from @Valid and @Validated annotations.
     *
     * @param ex the Exception
     * @return error response with 400 status
     */
     @ExceptionHandler(ConstraintViolationException.class)
     public ResponseEntity<?> handleConstraintViolationException(ConstraintViolationException ex){
         log.warn("Constraint Violation:{}" , ex.getMessage());
         Map<String,Object> errorResponse = buildErrorResponse(
                 HttpStatus.BAD_REQUEST.value(),
                 "Invalid Argument",
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
     public ResponseEntity<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex){
        String message = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s",
                ex.getValue(),
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );

        Map<String,Object> errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                message,
                ex.getMessage());

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

    private Map<String, Object> buildErrorResponse(int  status, String accountNotFound, String message) {

     Map<String, Object> errorResponse = new HashMap<>();
     errorResponse.put("timestamp",LocalDateTime.now());
     errorResponse.put("status", status);
     errorResponse.put("error", accountNotFound);
     errorResponse.put("message", message);

     return errorResponse;
 }


}
