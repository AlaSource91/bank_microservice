package com.alaeldin.bank_query_service.consumer;

import com.alaeldin.bank_query_service.handler.EventHandleRegistry;
import com.alaeldin.bank_query_service.model.event.AccountEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka consumer for bank account events (CQRS read-side).
 * Consumes events from the bank-events topic and delegates processing
 * to the EventHandleRegistry to update the read model.
 * Implements idempotency using Redis to prevent duplicate event processing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BankEventConsumer {

    private final ObjectMapper objectMapper;
    private final EventHandleRegistry eventHandleRegistry;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.consumer.idempotency.processing-ttl-seconds:60}")
    private int processingTtlSeconds;

    @Value("${app.consumer.idempotency.processed-ttl-minutes:10}")
    private int processedTtlMinutes;

    /**
     * Consumes bank account events from Kafka topic.
     * Uses manual acknowledgment to ensure events are only committed after successful processing.
     * Implements idempotency check using Redis to prevent duplicate processing.
     *
     * @param message the JSON payload of the event
     * @param partition the Kafka partition the message was received from
     * @param offset the offset of the message in the partition
     * @param acknowledgment manual acknowledgment handler
     */
    @KafkaListener(
            topics = "${app.kafka.topic}",
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
            // Deserialize the event
            AccountEvent accountEvent = deserializeEvent(message);

            // Validate event structure
            validateEvent(accountEvent, offset);

            // Check for duplicate events (idempotency)
            IdempotencyDecision decision = checkIdempotency(accountEvent);

            if (decision == IdempotencyDecision.ALREADY_PROCESSED) {
                log.warn("Duplicate event detected - EventId: {}, Offset: {}. Skipping processing.",
                        accountEvent.getEventId(), offset);
                acknowledgment.acknowledge();
                return;
            }

            if (decision == IdempotencyDecision.PROCESSING) {
                log.warn("Event currently being processed or recovering from crash - EventId: {}, Offset: {}. Skipping for now.",
                        accountEvent.getEventId(), offset);
                acknowledgment.acknowledge();
                return;
            }

            // Process the event
            log.info("Processing event - Type: {}, Account: {}, EventId: {}",
                    accountEvent.getEventType(),
                    accountEvent.getAccountNumber(),
                    accountEvent.getEventId()
            );

            eventHandleRegistry.handleEvent(
                    accountEvent,
                    accountEvent.getAccountNumber(),
                    accountEvent.getId()
            );

            // Mark event as processed (for idempotency)
            markEventAsProcessed(accountEvent);

            // Acknowledge successful processing
            acknowledgment.acknowledge();
            log.info("Event processed successfully - EventId: {}, Offset: {}",
                    accountEvent.getEventId(), offset);

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize event at offset {}: {}. Payload: {}",
                    offset, e.getMessage(), message, e);
            // Acknowledge to skip malformed messages and prevent blocking
            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid event data at offset {}: {}. Payload: {}",
                    offset, e.getMessage(), message, e);
            // Acknowledge to skip invalid events
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Unexpected error processing event at offset {}: {}",
                    offset, e.getMessage(), e);
            // Acknowledge to prevent infinite reprocessing
            // Consider implementing a dead-letter queue for failed events
            acknowledgment.acknowledge();
        }
    }

    /**
     * Deserializes JSON message to AccountEvent.
     *
     * @param message JSON string
     * @return AccountEvent object
     * @throws JsonProcessingException if deserialization fails
     */
    private AccountEvent deserializeEvent(String message) throws JsonProcessingException {
        return objectMapper.readValue(message, AccountEvent.class);
    }

    /**
     * Validates that the event contains required fields.
     *
     * @param event the account event to validate
     * @param offset the Kafka offset (for logging)
     * @throws IllegalArgumentException if validation fails
     */
    private void validateEvent(AccountEvent event, long offset) {
        if (event == null) {
            throw new IllegalArgumentException("Event is null at offset: " + offset);
        }

        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is missing at offset: " + offset);
        }

        if (event.getAccountNumber() == null || event.getAccountNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Account number is missing at offset: " + offset);
        }

        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is missing at offset: " + offset);
        }
    }
    /**
     * Marks an event as successfully processed in Redis.
     * Updates the state from "processing" to "processed" with longer TTL.
     * This prevents the event from being reprocessed even after crash recovery window.
     *
     * @param event the account event to mark as processed
     */
    private void markEventAsProcessed(AccountEvent event) {
        try {
            String key = buildRedisKey(event.getEventId());
            // Update state to "processed" with longer TTL
            redisTemplate.opsForValue()
                    .set(key, "processed", processedTtlMinutes, TimeUnit.MINUTES);
            log.debug("Event marked as processed in Redis - EventId: {}, TTL: {}min",
                    event.getEventId(), processedTtlMinutes);

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failed when marking event as processed: {}. Event was processed but not cached.",
                    event.getEventId(), e);
            // Don't fail the entire operation if Redis is down

        } catch (Exception e) {
            log.error("Unexpected error when marking event as processed in Redis: {}",
                    event.getEventId(), e);
            // Don't fail the entire operation
        }
    }

    /**
     * Builds Redis key for event tracking.
     *
     * @param eventId the event ID
     * @return Redis key
     */
    private String buildRedisKey(String eventId) {
        return "event:processed:" + eventId;
    }

    /**
     * Checks idempotency state of an event using Redis distributed lock.
     * States:
     * - NEW: First time processing, lock acquired
     * - PROCESSING: Currently being processed or recovering from crash (skip, will retry)
     * - ALREADY_PROCESSED: Successfully completed (skip, duplicate)
     *
     * @param accountEvent the event to check
     * @return IdempotencyDecision enum indicating the state
     */
    private IdempotencyDecision checkIdempotency(AccountEvent accountEvent) {
        try {
            String key = buildRedisKey(accountEvent.getEventId());
            String state = redisTemplate.opsForValue().get(key);

            // Check current state
            if ("processed".equals(state)) {
                log.debug("Event already processed - EventId: {}", accountEvent.getEventId());
                return IdempotencyDecision.ALREADY_PROCESSED;
            } else if ("processing".equals(state)) {
                log.debug("Event currently processing or recovering - EventId: {}", accountEvent.getEventId());
                return IdempotencyDecision.PROCESSING;
            }

            // State is null or unknown - try to acquire lock
            Boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, "processing", processingTtlSeconds, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(lockAcquired)) {
                log.debug("Processing lock acquired - EventId: {}, TTL: {}s",
                        accountEvent.getEventId(), processingTtlSeconds);
                return IdempotencyDecision.NEW;
            } else {
                // Another consumer got the lock
                log.debug("Failed to acquire lock - EventId: {}", accountEvent.getEventId());
                return IdempotencyDecision.PROCESSING;
            }

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failed during idempotency check for EventId: {}. Proceeding with processing (degraded mode).",
                    accountEvent.getEventId(), e);
            // If Redis is down, proceed with processing to avoid blocking
            return IdempotencyDecision.NEW;

        } catch (Exception e) {
            log.error("Unexpected error during idempotency check for EventId: {}. Proceeding with processing.",
                    accountEvent.getEventId(), e);
            // If there's any other error, proceed with processing
            return IdempotencyDecision.NEW;
        }
    }

    /**
     * Represents the idempotency state of an event.
     */
    private enum IdempotencyDecision {
        /** Event is new and should be processed */
        NEW,

        /** Event is currently being processed or recovering from crash (skip for now) */
        PROCESSING,

        /** Event was already processed successfully (skip, it's a duplicate) */
        ALREADY_PROCESSED
    }
}
