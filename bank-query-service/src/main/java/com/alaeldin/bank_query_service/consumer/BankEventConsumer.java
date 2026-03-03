package com.alaeldin.bank_query_service.consumer;

import com.alaeldin.bank_query_service.handler.EventHandleRegistry;
import com.alaeldin.bank_query_service.model.event.AccountEvent;
import com.alaeldin.bank_query_service.model.event.BaseEvent;
import com.alaeldin.bank_query_service.service.EventDeserializationService;
import com.alaeldin.bank_query_service.service.IdempotencyService;
import com.alaeldin.bank_query_service.service.IdempotencyService.IdempotencyState;
import com.alaeldin.bank_query_service.validator.EventValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for bank account events (CQRS read-side).
 * Consumes events from multiple topics and delegates processing to the EventHandleRegistry.
 * Implements idempotency using Redis to prevent duplicate event processing.
 *
 * <p>Key Features:</p>
 * <ul>
 *   <li>Manual acknowledgment for reliable message processing</li>
 *   <li>Distributed idempotency checking via Redis</li>
 *   <li>Comprehensive error handling with automatic skip of invalid messages</li>
 *   <li>Support for multiple event topics (account, transaction, ledger)</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BankEventConsumer {

    private final EventDeserializationService deserializationService;
    private final EventValidator eventValidator;
    private final IdempotencyService idempotencyService;
    private final EventHandleRegistry eventHandleRegistry;

    /**
     * Consumes bank events from Kafka topics.
     * Uses manual acknowledgment to ensure events are only committed after successful processing.
     *
     * @param message the JSON payload of the event
     * @param partition the Kafka partition the message was received from
     * @param offset the offset of the message in the partition
     * @param acknowledgment manual acknowledgment handler
     */
    @KafkaListener(
            topics = {"bank.account.events", "bank.transaction.events", "bank.ledger.events"},
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("Received event - Partition: {}, Offset: {}", partition, offset);
        log.debug("Event payload: {}", message);

        try {
            BaseEvent event = deserializeAndValidate(message, offset);

            if (shouldSkipProcessing(event, offset)) {
                acknowledgment.acknowledge();
                return;
            }

            processEvent(event);

            idempotencyService.markAsProcessed(event.getEventId());
            acknowledgment.acknowledge();

            log.info("Event processed successfully - EventId: {}, Offset: {}",
                    event.getEventId(), offset);

        } catch (EventDeserializationService.EventDeserializationException e) {
            handleDeserializationError(e, message, offset, acknowledgment);

        } catch (EventValidator.EventValidationException e) {
            handleValidationError(e, message, offset, acknowledgment);

        } catch (Exception e) {
            handleUnexpectedError(e, offset);
             throw e;
        }
    }

    /**
     * Deserializes and validates the event message.
     *
     * @param message the raw JSON message
     * @param offset the Kafka offset
     * @return the validated AccountEvent
     */
    private BaseEvent deserializeAndValidate(String message, long offset) {
        BaseEvent event = deserializationService.deserialize(message);
        eventValidator.validate(event, offset);
        return event;
    }

    /**
     * Determines if event processing should be skipped based on idempotency check.
     *
     * @param event the account event
     * @param offset the Kafka offset
     * @return true if processing should be skipped, false otherwise
     */
    private boolean shouldSkipProcessing(BaseEvent event, long offset) {
        IdempotencyState state = idempotencyService.checkState(event.getEventId());

        if (state == IdempotencyState.ALREADY_PROCESSED) {
            log.warn("Duplicate event detected - EventId: {}, Offset: {}. Skipping processing.",
                    event.getEventId(), offset);
            return true;
        }

        if (state == IdempotencyState.PROCESSING) {
            log.warn("Event currently being processed or recovering from crash - " +
                    "EventId: {}, Offset: {}. Skipping for now.", event.getEventId(), offset);
            return true;
        }

        return false;
    }

    /**
     * Processes the event by delegating to the event handler registry.
     *
     * @param event the account event to process
     */
    private void processEvent(BaseEvent event) {
        log.info("Processing event - Type: {}, Account: {}, EventId: {}",
                event.getEventType(), event.getAccountNumber(), event.getEventId());

        eventHandleRegistry.handleEvent(
                event,
                event.getAccountNumber(),
                event.getId()
        );
    }

    /**
     * Handles deserialization errors by logging and acknowledging to skip malformed messages.
     */
    private void handleDeserializationError(
            EventDeserializationService.EventDeserializationException e,
            String message,
            long offset,
            Acknowledgment acknowledgment
    ) {
        log.error("Failed to deserialize event at offset {}: {}. Payload: {}",
                offset, e.getMessage(), truncateMessage(message), e);
        acknowledgment.acknowledge();
    }

    /**
     * Handles validation errors by logging and acknowledging to skip invalid events.
     */
    private void handleValidationError(
            EventValidator.EventValidationException e,
            String message,
            long offset,
            Acknowledgment acknowledgment
    ) {
        log.error("Invalid event data at offset {}: {}. Payload: {}",
                offset, e.getMessage(), truncateMessage(message), e);
        acknowledgment.acknowledge();
    }

    /**
     * Handles unexpected errors by logging and acknowledging to prevent infinite reprocessing.
     */
    private void handleUnexpectedError(
            Exception e,
            long offset
    ) {
        log.error("Unexpected error processing event at offset {}: {}. " +
                "Acknowledging to prevent infinite reprocessing. Consider implementing a DLQ.",
                offset, e.getMessage(), e);
       // acknowledgment.acknowledge();
    }

    /**
     * Truncates message for logging to prevent excessive log output.
     *
     * @param message the message to truncate
     * @return truncated message
     */
    private String truncateMessage(String message) {
        if (message == null) {
            return "null";
        }
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
