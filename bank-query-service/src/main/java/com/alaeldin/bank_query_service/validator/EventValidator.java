package com.alaeldin.bank_query_service.validator;

import com.alaeldin.bank_query_service.model.event.AccountEvent;
import com.alaeldin.bank_query_service.model.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validator for account events to ensure data integrity.
 */
@Component
@Slf4j
public class EventValidator {

    /**
     * Validates that the event contains all required fields.
     *
     * @param event the account event to validate
     * @param offset the Kafka offset (for logging context)
     * @throws EventValidationException if validation fails
     */
    public void validate(BaseEvent event, long offset) {
        if (event == null) {
            throw new EventValidationException("Event is null at offset: " + offset);
        }

        validateEventType(event, offset);
      //  validateAccountNumber(event, offset);
        validateEventId(event, offset);
    }

    /**
     * Validates the event type field.
     */
    private void validateEventType(BaseEvent event, long offset) {
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new EventValidationException(
                    String.format("Event type is missing at offset: %d, eventId: %s",
                            offset, event.getEventId())
            );
        }
    }

    /**
     * Validates the account number field.
     */
    private void validateAccountNumber(AccountEvent event, long offset) {
        if (event.getAccountNumber() == null || event.getAccountNumber().trim().isEmpty()) {
            throw new EventValidationException(
                    String.format("Account number is missing at offset: %d, eventId: %s",
                            offset, event.getEventId())
            );
        }
    }

    /**
     * Validates the event ID field.
     */
    private void validateEventId(BaseEvent event, long offset) {
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new EventValidationException(
                    String.format("Event ID is missing at offset: %d", offset)
            );
        }
    }

    /**
     * Exception thrown when event validation fails.
     */
    public static class EventValidationException extends IllegalArgumentException {
        public EventValidationException(String message) {
            super(message);
        }
    }
}

