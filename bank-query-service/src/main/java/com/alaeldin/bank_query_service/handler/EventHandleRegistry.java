package com.alaeldin.bank_query_service.handler;

import com.alaeldin.bank_query_service.model.event.AccountEvent;
import com.alaeldin.bank_query_service.model.event.BaseEvent;
import com.alaeldin.bank_query_service.model.event.LedgerEvent;
import com.alaeldin.bank_query_service.model.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registry for routing events to appropriate handlers based on event type.
 * Acts as a central dispatcher for all incoming events in the CQRS read model.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventHandleRegistry {

    private final AccountEventHandler accountEventHandler;
    private final LedgerEventHandler ledgerEventHandler;
    private final TransactionEventHandler transactionEventHandler;

    /**
     * Routes an event to the appropriate handler based on event type.
     */
    public void handleEvent(BaseEvent event, String accountNumber, String id) {
        if (event == null) {
            log.error("Received null event, skipping processing");
            return;
        }

        String eventType = event.getEventType();
        log.info("Routing event to handler - Type: {}, EventId: {}", eventType, event.getEventId());

        try {
            switch (eventType) {
                case "ACCOUNT_CREATED"       -> handleAccountCreated(event);
                case "ACCOUNT_FROZEN"        -> handleAccountFrozen(event, accountNumber);
                case "ACCOUNT_UPDATED"       -> handleAccountUpdated(event, id);
                case "LEDGER_ENTRY_CREATED"  -> handleLedgerEntry(event);
                case "TRANSACTION_COMPLETED", "transaction_completed" -> handleTransactionCompleted(event);
                case "TRANSACTION_FAILED",   "transaction_failed"     -> handleTransactionFailed(event);
                default -> log.warn("Unhandled event type: {} - EventId: {}. Skipping.", eventType, event.getEventId());
            }
            log.info("Successfully processed event - Type: {}, EventId: {}", eventType, event.getEventId());

        } catch (ClassCastException e) {
            log.error("Event type mismatch for type: {}. EventId: {}", eventType, event.getEventId(), e);
            throw new EventHandlingException("Event type mismatch for: " + eventType, e);
        } catch (Exception e) {
            log.error("Error handling event - Type: {}, EventId: {}. Error: {}", eventType, event.getEventId(), e.getMessage(), e);
            throw new EventHandlingException("Failed to handle event: " + eventType, e);
        }
    }

    private void handleAccountCreated(BaseEvent event) {
        if (event instanceof AccountEvent accountEvent) {
            accountEventHandler.handleAccountCreated(accountEvent);
        } else {
            throw new ClassCastException("Expected AccountEvent but got: " + event.getClass().getSimpleName());
        }
    }

    public void handleTransactionCompleted(BaseEvent event) {
        if (event instanceof TransactionEvent transactionEvent) {
            log.debug("Delegating to TransactionEventHandler.handleTransactionSuccessful - Transaction: {}",
                    transactionEvent.getTransactionId());
            transactionEventHandler.handleTransactionSuccessful(transactionEvent);
        } else {
            throw new ClassCastException("Expected TransactionEvent but got: " + event.getClass().getSimpleName());
        }
    }

    public void handleTransactionFailed(BaseEvent event) {
        if (event instanceof TransactionEvent transactionEvent) {
            log.debug("Delegating to TransactionEventHandler.handleTransactionFailed - Transaction: {}",
                    transactionEvent.getTransactionId());
            transactionEventHandler.handleTransactionFailed(transactionEvent);
        } else {
            throw new ClassCastException("Expected TransactionEvent but got: " + event.getClass().getSimpleName());
        }
    }

    private void handleAccountFrozen(BaseEvent event, String accountNumber) {
        if (event instanceof AccountEvent accountEvent) {
            accountEventHandler.handleAccountFrozen(accountEvent, accountNumber);
        } else {
            throw new ClassCastException("Expected AccountEvent but got: " + event.getClass().getSimpleName());
        }
    }

    private void handleAccountUpdated(BaseEvent event, String id) {
        if (event instanceof AccountEvent accountEvent) {
            accountEventHandler.handleAccountUpdated(accountEvent, id);
        } else {
            throw new ClassCastException("Expected AccountEvent but got: " + event.getClass().getSimpleName());
        }
    }

    private void handleLedgerEntry(BaseEvent event) {
        if (event instanceof LedgerEvent ledgerEvent) {
            ledgerEventHandler.handleLedgerEntryCreated(ledgerEvent);
        } else {
            throw new ClassCastException("Expected LedgerEvent but got: " + event.getClass().getSimpleName());
        }
    }

    public static class EventHandlingException extends RuntimeException {
        public EventHandlingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
