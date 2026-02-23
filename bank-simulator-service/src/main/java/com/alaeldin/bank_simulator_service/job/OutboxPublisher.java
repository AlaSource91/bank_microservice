package com.alaeldin.bank_simulator_service.job;

import com.alaeldin.bank_simulator_service.model.OutboxEvent;
import com.alaeldin.bank_simulator_service.service.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


/**
 * OutboxPublisher is responsible for publishing events
 * from the outbox table to Kafka.
 *
 * This component implements the Outbox Pattern
 * to ensure reliable event delivery
 * in a distributed system. It periodically polls the outbox table for unpublished
 * events and publishes them to Kafka topics.
 *
 * Key Features:
 * - Batch processing for improved performance
 * - Automatic retry with exponential backoff
 * - Comprehensive error handling and logging
 * - Graceful handling of serialization failures
 * - Proper transaction management
 *
 * @author Alaeldin
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
@EnableScheduling
public class OutboxPublisher {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.transaction-events:bank.transaction.events}")
    private String transactionEventsTopic;

    @Value("${app.kafka.topic.account-events:bank.account.events}")
    private String accountEventsTopic;

    @Value("${app.outbox.batch-size:20}")
    private int batchSize;

    @Value("${app.outbox.max-retries:3}")
    private int maxRetries;

    @Value("${app.outbox.publish-timeout-seconds:30}")
    private int publishTimeoutSeconds;

    /**
     * Scheduled method that runs every 10 seconds to publish outbox events to Kafka.
     * This method processes events in batches for better performance.
     */
    @Scheduled(fixedDelay = 10000)
    public void publishOutboxEvents() {
        try {
            List<OutboxEvent> events = outboxService.lockBatchForPublishing(batchSize);

            if (CollectionUtils.isEmpty(events)) {
                log.debug("No unpublished outbox events found");
                return;
            }

            log.info(" Processing {} outbox events for publishing", events.size());

            for (OutboxEvent event : events) {
                publishSingleEvent(event);
            }

            log.info("Completed processing {} outbox events", events.size());

        } catch (Exception ex) {
            log.error(" Error during scheduled outbox event publishing: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Publishes a single event to Kafka with proper error handling, timeout, and callback management.
     *
     * @param event The OutboxEvent to be published
     */
    private void publishSingleEvent(OutboxEvent event) {
        if (event == null) {
            log.warn("Attempted to publish null event, skipping");
            return;
        }

        try {
            // Serialize the event payload
            String payload = serializeEventPayload(event);

            // Create Kafka message key using aggregate ID for partitioning
            String messageKey = event.getAggregateId();

            // Determine the correct topic based on aggregate type
            String targetTopic = determineTargetTopic(event);

            log.debug(" Publishing outbox event: id={}, aggregateId={}, aggregateType={}, eventType={}, topic={}, retryCount={}/{}",
                     event.getId(), event.getAggregateId(), event.getAggregateType(), event.getEventType(), targetTopic,
                     event.getRetryCount(), event.getMaxRetries());

            // Send message to Kafka asynchronously with timeout
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(targetTopic, messageKey, payload);

            // Add timeout handling to prevent hanging
            CompletableFuture<SendResult<String, String>> timeoutFuture = future
                    .orTimeout(publishTimeoutSeconds, TimeUnit.SECONDS)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.warn(" Kafka send operation timed out or failed for event id: {}, error: {}",
                                    event.getId(), exception.getMessage());
                        }
                        handleKafkaResult(event, result, exception, targetTopic);
                    });

        } catch (Exception ex) {
            log.error(" Exception occurred while publishing event id: {}, error: {}",
                     event.getId(), ex.getMessage(), ex);
            outboxService.markEventAsFailed(event.getId(), ex.getMessage());
        }
    }

    /**
     * Safely retrieves the event payload as JSON string.
     * The eventPayload is already serialized as JSON when stored in the outbox.
     *
     * @param event The outbox event containing the payload
     * @return JSON string payload or empty JSON object if payload is null
     */
    private String serializeEventPayload(OutboxEvent event) {
        try {
            String eventPayload = event.getEventPayload();
            if (eventPayload == null || eventPayload.trim().isEmpty()) {
                log.warn(" Event payload is null or empty for event id: {}", event.getId());
                return "{}"; // Return empty JSON object for null/empty payload
            }

            return eventPayload;

        } catch (Exception ex) {
            log.error(" Failed to retrieve event payload for event id: {}, error: {}",
                     event.getId(), ex.getMessage(), ex);
            return "{}";
        }
    }

    /**
     * Handles the result of Kafka message publishing.
     *
     * @param event The original outbox event
     * @param result The Kafka send result (null if failed)
     * @param exception The exception if publishing failed (null if successful)
     * @param targetTopic The Kafka topic the event was published to
     */
    private void handleKafkaResult(OutboxEvent event, SendResult<String, String> result, Throwable exception, String targetTopic) {
        if (exception == null && result != null) {
            // Success case
            handleSuccessfulPublishing(event, result, targetTopic);
        } else {
            // Failure case
            handleFailedPublishing(event, exception, targetTopic);
        }
    }

    /**
     * Handles successful event publishing.
     */
    private void handleSuccessfulPublishing(OutboxEvent event, SendResult<String, String> result, String targetTopic) {
        try {
            outboxService.markEventAsPublished(event.getId(), event.getIdempotencyKey());

            log.info("Successfully published event: id={}, aggregateId={}, aggregateType={}, topic={}, partition={}, offset={}",
                    event.getId(),
                    event.getAggregateId(),
                    event.getAggregateType(),
                    targetTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (Exception ex) {
            log.error(" Failed to mark event as published: eventId={}, error={}",
                     event.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Handles failed event publishing with comprehensive error logging and diagnosis.
     */
    private void handleFailedPublishing(OutboxEvent event, Throwable exception, String targetTopic) {
        String errorMessage = exception != null ? exception.getMessage() : "Unknown error occurred";
        String rootCause = exception != null ? getRootCauseMessage(exception) : "Unknown";

        try {
            outboxService.markEventAsFailed(event.getId(), errorMessage);

            log.error("  KAFKA SEND FAILED - Event Publishing Error Details:");
            log.error("    Event ID: {}", event.getId());
            log.error("     Aggregate ID: {}", event.getAggregateId());
            log.error("     Aggregate Type: {}", event.getAggregateType());
            log.error("     Event Type: {}", event.getEventType());
            log.error("     Topic: {}", targetTopic);
            log.error("     Error Message: {}", errorMessage);
            log.error("     Root Cause: {}", rootCause);
            log.error("     Created At: {}", event.getCreatedAt());

            // Log additional troubleshooting info
            if (exception != null) {
                log.error("    🔧 Exception Type: {}", exception.getClass().getSimpleName());

                // Check for specific Kafka-related errors
                if (isKafkaConnectivityIssue(exception)) {
                    log.error("     DIAGNOSIS: Kafka connectivity issue detected!");
                    log.error("     SOLUTION: Check if Kafka is running on localhost:9092");
                    log.error("      Run: docker-compose ps to verify Kafka status");
                } else if (isKafkaTimeoutIssue(exception)) {
                    log.error("     DIAGNOSIS: Kafka timeout issue detected!");
                    log.error("     SOLUTION: Consider increasing timeout values");
                } else if (isSerializationIssue(exception)) {
                    log.error("     DIAGNOSIS: Event serialization issue detected!");
                    log.error("     SOLUTION: Check event payload format");
                }
            }

        } catch (Exception ex) {
            log.error(" Failed to mark event as failed: eventId={}, originalError={}, markFailedError={}",
                     event.getId(), errorMessage, ex.getMessage(), ex);
        }
    }

    /**
     * Helper method to get root cause of exception
     */
    private String getRootCauseMessage(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getMessage();
    }

    /**
     * Helper method to check if error is related to Kafka connectivity
     */
    private boolean isKafkaConnectivityIssue(Throwable exception) {
        String message = exception.getMessage();
        return message != null && (
            message.contains("Connection to node") ||
            message.contains("Failed to send") ||
            message.contains("Connection refused") ||
            message.contains("Network is unreachable") ||
            message.contains("bootstrap servers")
        );
    }

    /**
     * Helper method to check if error is related to timeout
     */
    private boolean isKafkaTimeoutIssue(Throwable exception) {
        String message = exception.getMessage();
        return message != null && (
            message.contains("timeout") ||
            message.contains("Timeout") ||
            message.contains("TIMEOUT") ||
            exception instanceof java.util.concurrent.TimeoutException
        );
    }

    /**
     * Helper method to check if error is related to serialization
     */
    private boolean isSerializationIssue(Throwable exception) {
        String message = exception.getMessage();
        return message != null && (
            message.contains("serialization") ||
            message.contains("serialize") ||
            message.contains("JSON") ||
            exception instanceof com.fasterxml.jackson.core.JsonProcessingException
        );
    }

    /**
     * Determines the target Kafka topic based on the aggregate type.
     * Routes BANK_ACCOUNT events to account-events topic and TRANSACTION events to transaction-events topic.
     *
     * @param event The outbox event
     * @return The target Kafka topic name
     */
    private String determineTargetTopic(OutboxEvent event) {
        String aggregateType = event.getAggregateType();

        if ("BANK_ACCOUNT".equalsIgnoreCase(aggregateType)) {
            log.debug("Routing event to account-events topic: eventId={}, aggregateType={}",
                     event.getId(), aggregateType);
            return accountEventsTopic;
        } else if ("TRANSACTION".equalsIgnoreCase(aggregateType)) {
            log.debug("Routing event to transaction-events topic: eventId={}, aggregateType={}",
                     event.getId(), aggregateType);
            return transactionEventsTopic;
        } else {
            log.warn("Unknown aggregate type '{}' for event id={}. Defaulting to transaction-events topic.",
                    aggregateType, event.getId());
            return transactionEventsTopic;
        }
    }
}
