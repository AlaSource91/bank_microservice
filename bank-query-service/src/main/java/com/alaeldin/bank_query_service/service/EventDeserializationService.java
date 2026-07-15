package com.alaeldin.bank_query_service.service;

import com.alaeldin.bank_query_service.model.event.AccountEvent;
import com.alaeldin.bank_query_service.model.event.BaseEvent;
import com.alaeldin.bank_query_service.model.event.LedgerEvent;
import com.alaeldin.bank_query_service.model.event.SagaEvent;
import com.alaeldin.bank_query_service.model.event.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for deserializing event messages with polymorphic event type handling.
 * Supports multiple event types including AccountEvent, TransactionEvent, LedgerEvent, and SagaEvent.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventDeserializationService {

    private static final String EVENT_TYPE_FIELD = "eventType";

    // Account events
    private static final String ACCOUNT_CREATED = "ACCOUNT_CREATED";
    private static final String ACCOUNT_FROZEN  = "ACCOUNT_FROZEN";
    private static final String ACCOUNT_UPDATED = "ACCOUNT_UPDATED";

    // Transaction events
    private static final String TRANSACTION_COMPLETED           = "TRANSACTION_COMPLETED";
    private static final String TRANSACTION_COMPLETED_LOWERCASE = "transaction_completed";
    private static final String TRANSACTION_FAILED              = "TRANSACTION_FAILED";
    private static final String TRANSACTION_FAILED_LOWERCASE    = "transaction_failed";

    // Ledger events
    private static final String LEDGER_ENTRY_CREATED = "LEDGER_ENTRY_CREATED";

    // Saga lifecycle events
    private static final String SAGA_STARTED     = "saga.started";
    private static final String SAGA_COMPLETED   = "saga.completed";
    private static final String SAGA_FAILED      = "saga.failed";
    private static final String SAGA_COMPENSATED = "saga.compensated";

    // Debit step events
    private static final String DEBIT_REQUESTED  = "debit.requested";
    private static final String DEBIT_COMPLETED  = "debit.completed";
    private static final String DEBIT_FAILED     = "debit.failed";
    private static final String DEBIT_REVERSED   = "debit.reversed";

    // Credit step events
    private static final String CREDIT_REQUESTED = "credit.requested";
    private static final String CREDIT_COMPLETED = "credit.completed";
    private static final String CREDIT_FAILED    = "credit.failed";

    private final ObjectMapper objectMapper;

    /**
     * Deserializes a JSON message to the appropriate event type based on the eventType field.
     *
     * @param message the JSON string
     * @return the deserialized BaseEvent
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

            return deserializeByType(jsonNode, eventType);

        } catch (JsonProcessingException e) {
            throw new EventDeserializationException("Failed to deserialize event: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes JSON to the appropriate event class based on event type.
     */
    private BaseEvent deserializeByType(JsonNode jsonNode, String eventType) throws JsonProcessingException {
        return switch (eventType) {

            // ── Account ──────────────────────────────────────────────────────
            case ACCOUNT_CREATED, ACCOUNT_FROZEN, ACCOUNT_UPDATED -> {
                AccountEvent event = objectMapper.treeToValue(jsonNode, AccountEvent.class);
                log.debug("Successfully deserialized AccountEvent - EventId: {}, Type: {}",
                        event.getEventId(), eventType);
                yield event;
            }

            // ── Ledger ───────────────────────────────────────────────────────
            case LEDGER_ENTRY_CREATED -> {
                LedgerEvent event = objectMapper.treeToValue(jsonNode, LedgerEvent.class);
                log.debug("Successfully deserialized LedgerEvent - EventId: {}, Type: {}",
                        event.getEventId(), eventType);
                yield event;
            }

            // ── Transaction ──────────────────────────────────────────────────
            case TRANSACTION_COMPLETED, TRANSACTION_COMPLETED_LOWERCASE,
                 TRANSACTION_FAILED, TRANSACTION_FAILED_LOWERCASE -> {
                TransactionEvent event = objectMapper.treeToValue(jsonNode, TransactionEvent.class);
                log.debug("Successfully deserialized TransactionEvent - EventId: {}, Type: {}, TransactionId: {}",
                        event.getEventId(), eventType, event.getTransactionId());
                yield event;
            }

            // ── Saga / Debit / Credit ────────────────────────────────────────
            case SAGA_STARTED, SAGA_COMPLETED, SAGA_FAILED, SAGA_COMPENSATED,
                 DEBIT_REQUESTED, DEBIT_COMPLETED, DEBIT_FAILED, DEBIT_REVERSED,
                 CREDIT_REQUESTED, CREDIT_COMPLETED, CREDIT_FAILED -> {
                SagaEvent event = objectMapper.treeToValue(jsonNode, SagaEvent.class);
                log.debug("Successfully deserialized SagaEvent - EventId: {}, Type: {}",
                        event.getEventId(), eventType);
                yield event;
            }

            // ── Unknown ──────────────────────────────────────────────────────
            default -> {
                log.warn("Unknown event type: {}. Event will be skipped by the handler registry.", eventType);
                // Deserialize tolerantly so the consumer can still acknowledge the message.
                yield objectMapper.treeToValue(jsonNode, SagaEvent.class);
            }
        };
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
