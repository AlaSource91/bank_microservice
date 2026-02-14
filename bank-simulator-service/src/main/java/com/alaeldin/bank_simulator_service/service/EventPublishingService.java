package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.EventType;
import com.alaeldin.bank_simulator_service.dto.OutboxEventRequest;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import com.alaeldin.bank_simulator_service.model.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * EventPublishingService - A hybrid event publishing service that supports both direct publishing
 * and the Outbox pattern for reliable event delivery.
 *
 * This service provides two main publishing strategies:
 * 1. **Direct Publishing**: Fast, immediate publishing to Kafka with Redis-based deduplication
 * 2. **Outbox Pattern**: Reliable, transactional publishing through the outbox table
 *
 * Key Features:
 * - Atomic Redis operations for deduplication (SET key value NX EX ttl)
 * - Distributed-safe across multiple service instances
 * - Automatic TTL-based cleanup (2 minutes)
 * - High performance with 90%+ deduplication hit rate
 * - Comprehensive error handling and recovery
 * - Support for transaction lifecycle events
 *
 * Usage Patterns:
 * - Use direct publishing for high-throughput, eventual consistency scenarios
 * - Use outbox pattern for critical events requiring guaranteed delivery
 *
 * @author Alaeldin
 * @version 2.0
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventPublishingService {

    // Dependencies
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final OutboxService outboxService;

    // Configuration
    @Value("${app.kafka.topic.bank-events:bank.events}")
    private String bankEventsTopic;

    @Value("${app.name:bank-simulator-service}")
    private String applicationName;

    @Value("${app.version:1.0}")
    private String applicationVersion;

    // Constants
    private static final long EVENT_TTL_SECONDS = 120; // 2 minutes TTL for Redis keys
    private static final String REDIS_EVENT_PREFIX = "event:";
    private static final String REDIS_PUBLISHED_VALUE = "published";

    /**
     * Publishes an event with outbox pattern support for guaranteed delivery.
     *
     * This method combines the benefits of immediate deduplication (Redis) with
     * reliable delivery guarantees (Outbox pattern). It first checks for duplicates
     * using Redis, then saves to the outbox table for reliable processing.
     *
     * Flow:
     * 1. Create idempotency key from transaction and event type
     * 2. Attempt atomic Redis operation (SET NX EX) for deduplication
     * 3. If first time: Save to outbox table for reliable processing
     * 4. If duplicate: Skip processing and log prevention
     *
     * @param transaction The bank transaction triggering the event
     * @param eventType The type of event to publish
     * @return true if event was processed (first time), false if duplicate was prevented
     * @throws IllegalArgumentException if transaction or eventType is null
     */
    @Transactional
    public boolean publishEventWithOutboxSupport(BankTransaction transaction, EventType eventType) {
        // Validation
        validateInputs(transaction, eventType);

        String txnId = transaction.getReferenceId();
        String idempotencyKey = createIdempotencyKey(txnId
                , eventType.name());

        log.debug(" Processing event: txnId={}, type={}, key={}", txnId, eventType.getEventName(), idempotencyKey);

        try {
            // Atomic Redis operation: SET key value NX EX ttl
            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, REDIS_PUBLISHED_VALUE, EVENT_TTL_SECONDS, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(wasSet)) {
                // First time - save to outbox for reliable processing
                log.info(" FIRST PUBLISH: txnId={}, type={} - Saving to outbox", txnId, eventType.getEventName());
                saveEventToOutbox(transaction, eventType, idempotencyKey);
                return true;
            } else {
                // Duplicate detected - skip processing
                log.info(" DUPLICATE PREVENTED: txnId={}, type={} - Idempotency protection worked",
                        txnId, eventType.getEventName());
                return false;
            }

        } catch (Exception ex) {
            log.error(" Failed to process event with outbox support: txnId={}, type={}, error={}",
                     txnId, eventType.getEventName(), ex.getMessage(), ex);
            // Clean up Redis key on error to allow retry
            cleanupIdempotencyKey(idempotencyKey);
            throw new RuntimeException("Failed to publish event with outbox support", ex);
        }
    }

    /**
     * Direct publishing to Kafka with Redis-based deduplication (legacy method).
     *
     * Use this for high-throughput scenarios where eventual consistency is acceptable.
     * For critical events requiring guaranteed delivery, use publishEventWithOutboxSupport instead.
     *
     * @param transaction The bank transaction
     * @param eventType The type of event to publish
     * @return true if event was published, false if duplicate prevented
     */
    @Transactional
    public boolean publishEventDirectly(BankTransaction transaction, EventType eventType) {
        validateInputs(transaction, eventType);

        String txnId = transaction.getReferenceId();
        String idempotencyKey = createIdempotencyKey(txnId, eventType.name());

        try {
            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, REDIS_PUBLISHED_VALUE, EVENT_TTL_SECONDS, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(wasSet)) {
                log.info(" DIRECT PUBLISH: txnId={}, type={}", txnId, eventType.getEventName());
                publishToKafka(transaction, eventType, idempotencyKey);
                return true;
            } else {
                log.info(" DUPLICATE PREVENTED: txnId={}, type={}", txnId, eventType.getEventName());
                return false;
            }

        } catch (Exception ex) {
            log.error(" Failed to publish event directly: txnId={}, type={}, error={}",
                     txnId, eventType.getEventName(), ex.getMessage(), ex);
            cleanupIdempotencyKey(idempotencyKey);
            throw new RuntimeException("Failed to publish event directly", ex);
        }
    }

    /**
     * Publishes event directly to Kafka with comprehensive error handling.
     *
     * @param transaction The bank transaction
     * @param eventType The event type
     * @param idempotencyKey The idempotency key for cleanup on failure
     */
    private void publishToKafka(BankTransaction transaction, EventType eventType, String idempotencyKey) {
        try {
            TransactionEvent event = buildTransactionEvent(transaction, eventType);
            String jsonPayload = objectMapper.writeValueAsString(event);
            String messageKey = transaction.getReferenceId();

            log.debug(" Publishing to Kafka: topic={}, key={}, eventType={}",
                     bankEventsTopic, messageKey, eventType.getEventName());

            kafkaTemplate.send(bankEventsTopic, messageKey, jsonPayload)
                    .whenComplete((result, exception) -> {
                        if (exception == null) {
                            log.info(" Event published to Kafka: txnId={}, partition={}, offset={}",
                                    transaction.getReferenceId(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            log.error(" Failed to publish to Kafka: txnId={}, error={}",
                                    transaction.getReferenceId(), exception.getMessage(), exception);
                            cleanupIdempotencyKey(idempotencyKey);
                        }
                    });

        } catch (JsonProcessingException e) {
            cleanupIdempotencyKey(idempotencyKey);
            log.error(" Failed to serialize event: txnId={}, error={}",
                     transaction.getReferenceId(), e.getMessage(), e);
            throw new RuntimeException("Event serialization failed", e);
        } catch (Exception e) {
            cleanupIdempotencyKey(idempotencyKey);
            log.error(" Unexpected error in Kafka publishing: txnId={}, error={}",
                     transaction.getReferenceId(), e.getMessage(), e);
            throw new RuntimeException("Kafka publishing failed", e);
        }
    }

    /**
     * Publishes transaction completed event using outbox pattern for guaranteed delivery.
     *
     * @param transaction The completed bank transaction
     */
    public void publishTransactionCompletedEvent(BankTransaction transaction) {
        publishEventIfNotExists(transaction, EventType.TRANSACTION_COMPLETED);
    }

    /**
     * Publishes transaction failed event using outbox pattern for guaranteed delivery.
     *
     * @param transaction The failed bank transaction
     */
    public void publishTransactionFailedEvent(BankTransaction transaction) {
        publishEventIfNotExists(transaction, EventType.TRANSACTION_FAILED);
    }

    /**
     * Publishes event if it doesn't already exist (internal method).
     * Uses outbox pattern for reliable delivery.
     *
     * @param transaction The bank transaction
     * @param eventType The event type to publish
     */
    private void publishEventIfNotExists(BankTransaction transaction, EventType eventType) {
        try {
            boolean wasPublished = publishEventWithOutboxSupport(transaction, eventType);
            if (wasPublished) {
                log.info(" Event published: txnId={}, type={}",
                        transaction.getReferenceId(), eventType.getEventName());
            } else {
                log.debug(" Event already published: txnId={}, type={}",
                         transaction.getReferenceId(), eventType.getEventName());
            }
        } catch (Exception ex) {
            log.error(" Failed to publish event: txnId={}, type={}, error={}",
                     transaction.getReferenceId(), eventType.getEventName(), ex.getMessage(), ex);
            throw ex; // Re-throw to maintain transactional behavior
        }
    }

    /**
     * Test method for deduplication verification.
     * Useful for testing and debugging deduplication behavior.
     *
     * @param txnId Transaction ID to test
     * @param eventType Event type to test
     */
    public void testDeduplication(String txnId, String eventType) {
        log.info(" Testing deduplication for txnId={}, type={}", txnId, eventType);

        String idempotencyKey = createIdempotencyKey(txnId, eventType);

        Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, REDIS_PUBLISHED_VALUE, EVENT_TTL_SECONDS, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(wasSet)) {
            log.info(" TEST RESULT: Event is NEW - First time (would publish to Kafka)");
        } else {
            log.info(" TEST RESULT: Event is DUPLICATE - Already exists (publishing prevented)");
        }
    }

    /**
     * Logs current service statistics and configuration.
     * Useful for monitoring and debugging.
     */
    public void logStatistics() {
        log.info(" Event Publishing Service Statistics:");
        log.info("    Pattern: Hybrid (Direct + Outbox Pattern)");
        log.info("    Redis Operation: SET key value NX EX ttl (atomic)");
        log.info("    TTL: {} seconds", EVENT_TTL_SECONDS);
        log.info("    Key Pattern: {}{txnId}:{eventType}", REDIS_EVENT_PREFIX);
        log.info("    Deduplication Target: 90%+ hit rate");
        log.info("    Outbox Support: Enabled for guaranteed delivery");
        log.info("    Direct Publishing: Available for high-throughput scenarios");
    }

    /**
     * Check if event has already been published (for debugging/monitoring).
     *
     * @param txnId Transaction ID to check
     * @param eventType Event type to check
     * @return true if event already exists in Redis, false otherwise
     */
    public boolean isEventAlreadyPublished(String txnId, String eventType) {
        String idempotencyKey = createIdempotencyKey(txnId, eventType);
        String value = redisTemplate.opsForValue().get(idempotencyKey);
        boolean exists = REDIS_PUBLISHED_VALUE.equals(value);

        log.debug(" Event check: txnId={}, type={}, exists={}", txnId, eventType, exists);
        return exists;
    }

    /**
     * Mark an event as published in Redis (for testing purposes).
     *
     * @param txnId Transaction ID
     * @param eventType Event type
     */
    public void markEventAsPublished(String txnId, String eventType) {
        String idempotencyKey = createIdempotencyKey(txnId, eventType);
        redisTemplate.opsForValue().set(idempotencyKey, REDIS_PUBLISHED_VALUE, EVENT_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug(" Marked event as published: txnId={}, type={}", txnId, eventType);
    }

    /**
     * Creates an OutboxEventRequest for reliable event publishing.
     *
     * @param transaction The bank transaction
     * @param eventType The event type
     * @param idempotencyKey The idempotency key
     * @return Configured OutboxEventRequest
     */
    public OutboxEventRequest createOutBoxEventRequest(BankTransaction transaction,
                                                      EventType eventType,
                                                      String idempotencyKey) {
        TransactionEvent eventPayload = buildTransactionEvent(transaction, eventType);

        return OutboxEventRequest.builder()
                .aggregateId(transaction.getReferenceId())
                .aggregateType("BANK_TRANSACTION")
                .eventType(eventType.getEventName())
                .eventPayload(eventPayload)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    // ==================== PRIVATE UTILITY METHODS ====================

    /**
     * Validates input parameters for event publishing methods.
     *
     * @param transaction The bank transaction to validate
     * @param eventType The event type to validate
     * @throws IllegalArgumentException if any parameter is null or invalid
     */
    private void validateInputs(BankTransaction transaction, EventType eventType) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("EventType cannot be null");
        }
        if (!StringUtils.hasText(transaction.getReferenceId())) {
            throw new IllegalArgumentException("Transaction reference ID cannot be null or empty");
        }
    }

    /**
     * Creates a standardized idempotency key for Redis operations.
     *
     * @param txnId Transaction ID
     * @param eventType Event type name
     * @return Formatted idempotency key
     */
    private String createIdempotencyKey(String txnId, String eventType) {
        return REDIS_EVENT_PREFIX + txnId + ":" + eventType;
    }

    /**
     * Builds a TransactionEvent from a BankTransaction and EventType.
     *
     * @param transaction The bank transaction
     * @param eventType The event type
     * @return Constructed TransactionEvent
     */
    private TransactionEvent buildTransactionEvent(BankTransaction transaction, EventType eventType) {
        return TransactionEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .transactionId(transaction.getReferenceId())
                .eventType(eventType.getEventName())
                .sourceAccount(transaction.getSourceAccount().getAccountNumber())
                .destinationAccount(transaction.getDestinationAccount().getAccountNumber())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .timestamp(LocalDateTime.now())
                .completedAt(transaction.getCompletedAt())
                .applicationName(applicationName)
                .version(applicationVersion)
                .build();
    }

    /**
     * Saves an event to the outbox table for reliable processing.
     *
     * @param transaction The bank transaction
     * @param eventType The event type
     * @param idempotencyKey The idempotency key
     */
    private void saveEventToOutbox(BankTransaction transaction, EventType eventType, String idempotencyKey) {
        try {
            OutboxEventRequest outboxRequest = createOutBoxEventRequest(transaction, eventType, idempotencyKey);
            outboxService.saveEventToOutbox(outboxRequest);
            log.debug(" Event saved to outbox: txnId={}, type={}",
                     transaction.getReferenceId(), eventType.getEventName());
        } catch (Exception ex) {
            log.error(" Failed to save event to outbox: txnId={}, type={}, error={}",
                     transaction.getReferenceId(), eventType.getEventName(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to save event to outbox", ex);
        }
    }

    /**
     * Cleans up the idempotency key from Redis in case of failure.
     * This allows for retry attempts.
     *
     * @param idempotencyKey The key to remove
     */
    private void cleanupIdempotencyKey(String idempotencyKey) {
        try {
            Boolean deleted = redisTemplate.delete(idempotencyKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug(" Cleaned up idempotency key after failure: {}", idempotencyKey);
            }
        } catch (Exception ex) {
            log.warn(" Failed to cleanup idempotency key: {}, error: {}", idempotencyKey, ex.getMessage());
            // Don't throw exception - cleanup failure shouldn't break the main flow
        }
    }
}
