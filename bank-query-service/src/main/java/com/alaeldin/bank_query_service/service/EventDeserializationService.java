package com.alaeldin.bank_query_service.service;

import com.alaeldin.bank_query_service.model.event.AccountEvent;
import com.alaeldin.bank_query_service.model.event.BaseEvent;
import com.alaeldin.bank_query_service.model.event.LedgerEvent;
import com.alaeldin.bank_query_service.model.event.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for deserializing event messages with polymorphic event type handling.
 * Supports multiple event types including AccountEvent, TransactionEvent, and LedgerEvent.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventDeserializationService {

    private static final String EVENT_TYPE_FIELD = "eventType";
    private static final String ACCOUNT_CREATED = "ACCOUNT_CREATED";
    private static final String ACCOUNT_FROZEN = "ACCOUNT_FROZEN";
    private static final String ACCOUNT_UPDATED = "ACCOUNT_UPDATED";
    private static final String TRANSACTION_COMPLETED = "TRANSACTION_COMPLETED";
    private static final String TRANSACTION_COMPLETED_LOWERCASE = "transaction_completed";
    private static final String TRANSACTION_FAILED = "TRANSACTION_FAILED";
    private static final String TRANSACTION_FAILED_LOWERCASE = "transaction_failed";
    private static final String LEDGER_ENTRY_CREATED = "LEDGER_ENTRY_CREATED";

    private final ObjectMapper objectMapper;

    /**
     * Deserializes a JSON message to the appropriate event type based on the eventType field.
     * Currently supports AccountEvent and LedgerEvent types.
     *
     * @param message the JSON string
     * @return the deserialized AccountEvent (base type for all events)
     * @throws EventDeserializationException if deserialization fails
     */
    public BaseEvent deserialize(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);

            if (!jsonNode.has(EVENT_TYPE_FIELD)) {
                throw new EventDeserializationException("Missing eventType field in message");
            }

            String eventType = jsonNode.get(EVENT_TYPE_FIELD).asText();
            log.debug("Deserializing event of type: {}", eventType);

            BaseEvent event = deserializeByType(jsonNode, eventType);

            // Return the appropriate event type - supports AccountEvent, LedgerEvent, and TransactionEvent
            if (event instanceof AccountEvent || event instanceof LedgerEvent || event instanceof TransactionEvent) {
                return event;
            }

            throw new EventDeserializationException("Unsupported event type: " + eventType);

        } catch (JsonProcessingException e) {
            throw new EventDeserializationException("Failed to deserialize event: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes JSON to the appropriate event class based on event type.
     *
     * @param jsonNode the JSON node
     * @param eventType the event type string
     * @return the deserialized event
     * @throws JsonProcessingException if deserialization fails
     */
    private BaseEvent deserializeByType(JsonNode jsonNode, String eventType) throws JsonProcessingException {
        return switch (eventType) {
            case ACCOUNT_CREATED, ACCOUNT_FROZEN, ACCOUNT_UPDATED -> {
                AccountEvent event = objectMapper.treeToValue(jsonNode, AccountEvent.class);
                log.debug("Successfully deserialized AccountEvent - EventId: {}, Type: {}",
                        event.getEventId(), eventType);
                yield event;
            }

            case LEDGER_ENTRY_CREATED -> {
                LedgerEvent event = objectMapper.treeToValue(jsonNode, LedgerEvent.class);
                log.debug("Successfully deserialized LedgerEvent - EventId: {}, Type: {}",
                        event.getEventId(), eventType);
                yield event;
            }
            case TRANSACTION_COMPLETED, TRANSACTION_COMPLETED_LOWERCASE, TRANSACTION_FAILED, TRANSACTION_FAILED_LOWERCASE -> {
                TransactionEvent event = objectMapper.treeToValue(jsonNode, TransactionEvent.class);
                log.debug("Successfully deserialized TransactionEvent - EventId: {}, Type: {}, TransactionId: {}",
                        event.getEventId(), eventType, event.getTransactionId());
                yield event;
            }

            default -> {
                log.warn("Unknown event type: {}. Attempting to deserialize as AccountEvent.", eventType);
                // Fallback to AccountEvent for unknown types
                AccountEvent event = objectMapper.treeToValue(jsonNode, AccountEvent.class);
                yield event;
            }
        };
    }

    /**
     * Converts a LedgerEvent to an AccountEvent for compatibility.
     * This is a temporary solution until proper polymorphic event handling is implemented.
     *
     * @param ledgerEvent the ledger event to convert
     * @return an AccountEvent representation
     */
    private AccountEvent convertToAccountEvent(LedgerEvent ledgerEvent) {
        // For now, create a minimal AccountEvent from LedgerEvent data
        AccountEvent accountEvent = new AccountEvent();
        accountEvent.setEventId(ledgerEvent.getEventId());
        accountEvent.setEventType(ledgerEvent.getEventType());
        accountEvent.setAccountNumber(ledgerEvent.getAccountNumber());

        log.debug("Converted LedgerEvent to AccountEvent - EventId: {}", ledgerEvent.getEventId());
        return accountEvent;
    }

    /**
     * Exception thrown when event deserialization fails.
     */
    public static class EventDeserializationException extends RuntimeException {
        public EventDeserializationException(String message) {
            super(message);
        }

        public EventDeserializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
