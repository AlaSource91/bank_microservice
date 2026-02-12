package com.alaeldin.bank_simulator_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when attempting to create a bank account with a holder name that already exists.
 * This is a conflict exception (HTTP 409) rather than a bad request (400) because the data structure
 * is valid, but conflicts with existing data in the system.
 *
 * <p>This exception is automatically caught by the GlobalExceptionHandler which returns a standardized
 * API error response with HTTP status 409 CONFLICT.</p>
 *
 * @see com.alaeldin.bank_simulator_service.exception.GlobalExceptionHandler
 */
@Getter
@ResponseStatus(value = HttpStatus.CONFLICT)
public class AccountHolderNameAlreadyExist extends RuntimeException {

    /**
     * The name of the field that caused the conflict (e.g., "accountHolderName").
     */
    private final String fieldName;

    /**
     * The value of the field that already exists in the system.
     */
    private final String fieldValue;

    /**
     * Constructs an AccountHolderNameAlreadyExist exception with field name and value information.
     *
     * @param fieldName  the name of the field that caused the conflict (e.g., "accountHolderName")
     * @param fieldValue the value of the field that already exists (e.g., "John Doe")
     */
    public AccountHolderNameAlreadyExist(String fieldName, String fieldValue) {
        super(String.format("Account holder name already exists with %s : '%s'", fieldName, fieldValue));
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

}
