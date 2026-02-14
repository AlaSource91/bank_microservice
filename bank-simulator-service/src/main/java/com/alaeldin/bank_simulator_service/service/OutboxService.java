package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.OutBoxStatus;
import com.alaeldin.bank_simulator_service.dto.OutboxEventRequest;
import com.alaeldin.bank_simulator_service.exception.ResourceNotFoundException;
import com.alaeldin.bank_simulator_service.model.OutboxEvent;
import com.alaeldin.bank_simulator_service.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Save event to outbox with idempotency key linkage
     * This method ensures atomicity and prevents duplicate events
     *
     * @param outboxEventRequest the event request containing all necessary data
     * @return the saved OutboxEvent or existing event if idempotency key already exists
     */
    @Transactional
    public OutboxEvent saveEventToOutbox(OutboxEventRequest outboxEventRequest) {
        try {
            // Validate input
            validateOutboxEventRequest(outboxEventRequest);

            // Check for existing event first (idempotency)
            return outboxEventRepository.findByIdempotencyKey(outboxEventRequest.getIdempotencyKey())
                    .map(existingEvent -> {
                        log.debug("📦 Event with idempotency key {} already exists: id={}, status={}",
                                 outboxEventRequest.getIdempotencyKey(), existingEvent.getId(), existingEvent.getStatus());
                        return existingEvent;
                    })
                    .orElseGet(() -> createNewOutboxEvent(outboxEventRequest));

        } catch (DataIntegrityViolationException e) {
            // Handle concurrent insertion with same idempotency key
            log.debug("Concurrent insertion detected for idempotency key {}, fetching existing event",
                     outboxEventRequest.getIdempotencyKey());

            return outboxEventRepository.findByIdempotencyKey(outboxEventRequest.getIdempotencyKey())
                    .orElseThrow(() -> new RuntimeException("Failed to retrieve event after concurrent insertion", e));

        } catch (Exception e) {
            log.error(" Failed to save event to outbox: aggregateId={}, eventType={}, error={}",
                     outboxEventRequest.getAggregateId(), outboxEventRequest.getEventType(), e.getMessage(), e);
            throw new RuntimeException("Failed to save event to outbox", e);
        }
    }

    /**
     * Create a new outbox event
     */
    private OutboxEvent createNewOutboxEvent(OutboxEventRequest request) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(request.getEventPayload());

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(request.getAggregateId())
                    .aggregateType(request.getAggregateType())
                    .eventType(request.getEventType())
                    .eventPayload(jsonPayload)
                    .idempotencyKey(request.getIdempotencyKey())
                    .createdAt(LocalDateTime.now())
                    .status(OutBoxStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(3)
                    .build();

            OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);
            log.info(" Saved new event to outbox: id={}, aggregateId={}, eventType={}",
                    savedEvent.getId(), savedEvent.getAggregateId(), savedEvent.getEventType());

            return savedEvent;

        } catch (Exception e) {
            log.error(" Failed to create new outbox event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create new outbox event", e);
        }
    }

    /**
     * Mark event as successfully published
     * Updates the event status and optionally caches result in Redis
     *
     * @param eventId the ID of the event to mark as published
     * @param idempotencyKey the idempotency key for Redis caching
     */
    @Transactional
    public void markEventAsPublished(Long eventId, String idempotencyKey) {
        try {
            LocalDateTime publishedAt = LocalDateTime.now();

            // Try atomic update first (more efficient for concurrent scenarios)
            boolean updated = atomicStatusUpdate(eventId, OutBoxStatus.PROCESSING, OutBoxStatus.SENT, publishedAt);

            if (!updated) {
                // Fallback to manual update if not in PROCESSING status
                OutboxEvent event = findEventById(eventId);
                event.setStatus(OutBoxStatus.SENT);
                event.setPublishedAt(publishedAt);
                event.setErrorMessage(null); // Clear error message on success
                outboxEventRepository.save(event);

                log.debug(" Updated event status via fallback method: id={}", eventId);
            }

            // Cache in Redis for consumer notification (optional)
            cachePublishedEventInRedis(idempotencyKey);

            log.info(" Marked event as published: id={}", eventId);

        } catch (Exception e) {
            log.error(" Failed to mark event as published: eventId={}, error={}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to mark event as published", e);
        }
    }

    /**
     * Mark event as failed and schedule retry with exponential backoff
     *
     * @param eventId the ID of the event that failed
     * @param errorMessage optional error message for logging
     */
    @Transactional
    public void markEventAsFailed(Long eventId, String errorMessage) {
        try {
            OutboxEvent event = findEventById(eventId);

            // Increment retry count
            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessage(errorMessage);

            // Check if exceeded max retries
            if (event.getRetryCount() >= event.getMaxRetries()) {
                event.setStatus(OutBoxStatus.DEAD);
                event.setNextRetryAt(null);
                outboxEventRepository.save(event);

                log.error(" Event exceeded max retries and marked as DEAD: id={}, retryCount={}, error={}",
                         eventId, event.getRetryCount(), errorMessage);
                return;
            }

            // Set status back to PENDING for retry and calculate next retry with exponential backoff
            event.setStatus(OutBoxStatus.PENDING);
            int delayMinutes = (int) Math.pow(2, event.getRetryCount());
            event.setNextRetryAt(LocalDateTime.now().plusMinutes(delayMinutes));

            outboxEventRepository.save(event);

            log.warn(" Event retry scheduled: id={}, retryCount={}/{}, nextRetry={}, delayMinutes={}, error={}",
                    eventId, event.getRetryCount(), event.getMaxRetries(), event.getNextRetryAt(), delayMinutes, errorMessage);

        } catch (Exception e) {
            log.error(" Failed to mark event as failed: eventId={}, error={}", eventId, e.getMessage(), e);
            throw new RuntimeException("Failed to mark event as failed", e);
        }
    }

    /**
     * Get events ready for publishing considering retry timing
     *
     * @param batchSize maximum number of events to return
     * @return list of events ready for publishing
     */
    public List<OutboxEvent> getEventsReadyForPublishing(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        return outboxEventRepository.findEventsReadyForPublishing(now, batchSize);
    }

    /**
     * Lock and get a batch of events for distributed processing
     * This method uses database-level locking to prevent concurrent processing
     *
     * @param batchSize maximum number of events to lock and return
     * @return list of locked events ready for processing
     */
    @Transactional
    public List<OutboxEvent> lockBatchForPublishing(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        return outboxEventRepository.lockBatchForPublishing(now, batchSize);
    }



    /**
     * Mark multiple events as processing to prevent concurrent access
     *
     * @param eventIds list of event IDs to mark as processing
     * @return number of events successfully marked as processing
     */
    @Transactional
    public int markEventsAsProcessing(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return 0;
        }

        int updated = outboxEventRepository.markEventsAsProcessing(eventIds);
        log.info(" Marked {} events as PROCESSING", updated);
        return updated;
    }

    /**
     * Find and recover stale processing events (zombie processes)
     * These are events stuck in PROCESSING state beyond reasonable time
     *
     * @param staleThresholdMinutes minutes after which PROCESSING events are considered stale
     * @return list of stale events that need recovery
     */
    public List<OutboxEvent> findAndRecoverStaleEvents(int staleThresholdMinutes) {
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(staleThresholdMinutes);
        List<OutboxEvent> staleEvents = outboxEventRepository.findStaleProcessingEvents(staleThreshold);

        if (!staleEvents.isEmpty()) {
            log.warn(" Found {} stale PROCESSING events older than {} minutes",
                    staleEvents.size(), staleThresholdMinutes);

            // Reset stale events back to PENDING for retry
            for (OutboxEvent event : staleEvents) {
                resetEventToPending(event.getId(), "Recovered from stale PROCESSING state");
            }
        }

        return staleEvents;
    }

    /**
     * Reset an event back to PENDING status for retry
     *
     * @param eventId the ID of the event to reset
     * @param reason the reason for reset (for logging)
     */
    @Transactional
    public void resetEventToPending(Long eventId, String reason) {
        try {
            OutboxEvent event = findEventById(eventId);
            event.setStatus(OutBoxStatus.PENDING);
            event.setErrorMessage(reason);
            outboxEventRepository.save(event);

            log.info(" Reset event {} to PENDING: {}", eventId, reason);
        } catch (Exception e) {
            log.error(" Failed to reset event {} to PENDING: {}", eventId, e.getMessage(), e);
        }
    }

    /**
     * Get failed events that exceeded max retries
     *
     * @return list of permanently failed events
     */
    public List<OutboxEvent> getFailedEvents() {
        return outboxEventRepository.findFailedEvents();
    }

    /**
     * Find events by aggregate for debugging and tracking
     *
     * @param aggregateId the aggregate ID to search for
     * @param aggregateType the aggregate type to search for
     * @return list of events for the specified aggregate
     */
    public List<OutboxEvent> findEventsByAggregate(String aggregateId, String aggregateType) {
        return outboxEventRepository.findByAggregateIdAndAggregateTypeOrderByCreatedAtDesc(aggregateId, aggregateType);
    }

    /**
     * Cleanup old published events (housekeeping)
     *
     * @param olderThanDays delete SENT events older than this many days
     */
    @Transactional
    public void cleanupOldPublishedEvents(int olderThanDays) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(olderThanDays);
        try {
            outboxEventRepository.deleteOldPublishedEvents(cutoffTime);
            log.info(" Cleaned up old SENT events older than {} days", olderThanDays);
        } catch (Exception e) {
            log.error(" Failed to cleanup old published events: {}", e.getMessage(), e);
        }
    }

    /**
     * Atomically update event status from current to new status
     * This provides safe concurrent status updates
     *
     * @param eventId the ID of the event to update
     * @param currentStatus expected current status
     * @param newStatus new status to set
     * @param publishedAt timestamp for published events (can be null)
     * @return true if update was successful, false if event wasn't in expected status
     */
    @Transactional
    public boolean atomicStatusUpdate(Long eventId, OutBoxStatus currentStatus,
                                    OutBoxStatus newStatus, LocalDateTime publishedAt) {
        try {
            int updated = outboxEventRepository.updateEventStatus(eventId, currentStatus, newStatus, publishedAt);

            if (updated > 0) {
                log.debug(" Atomically updated event {} from {} to {}", eventId, currentStatus, newStatus);
                return true;
            } else {
                log.debug(" Event {} not in expected status {} for atomic update", eventId, currentStatus);
                return false;
            }

        } catch (Exception e) {
            log.error(" Failed atomic status update for event {}: {}", eventId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get outbox statistics for monitoring
     *
     * @return statistics object containing various metrics
     */
    public OutboxStatistics getOutboxStatistics() {
        long unpublishedCount = outboxEventRepository.countUnpublishedEvents();
        long sentCount = outboxEventRepository.countByStatus(OutBoxStatus.SENT);
        long failedCount = outboxEventRepository.countByStatus(OutBoxStatus.FAILED);
        long deadCount = outboxEventRepository.countByStatus(OutBoxStatus.DEAD);
        long processingCount = outboxEventRepository.countByStatus(OutBoxStatus.PROCESSING);

        return new OutboxStatistics(unpublishedCount, sentCount, failedCount, deadCount, processingCount);
    }

    // Private helper methods

    private void validateOutboxEventRequest(OutboxEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("OutboxEventRequest cannot be null");
        }
        if (request.getAggregateId() == null || request.getAggregateId().trim().isEmpty()) {
            throw new IllegalArgumentException("Aggregate ID cannot be null or empty");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or empty");
        }
        if (request.getEventType() == null || request.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type cannot be null or empty");
        }
    }

    private OutboxEvent findEventById(Long eventId) {
        return outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("OutboxEvent", "id", eventId.toString()));
    }

    private void cachePublishedEventInRedis(String idempotencyKey) {
        try {
            if (redisTemplate != null && idempotencyKey != null) {
                redisTemplate.opsForValue().set(
                    idempotencyKey,
                    "published",
                    java.time.Duration.ofMinutes(5) // 5 minutes TTL for consumers
                );
                log.debug(" Cached published event in Redis: key={}", idempotencyKey);
            }
        } catch (Exception e) {
            // Non-critical error - log but don't fail the transaction
            log.warn(" Failed to cache event in Redis: key={}, error={}", idempotencyKey, e.getMessage());
        }
    }

    // Inner class for statistics
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class OutboxStatistics {
        private final long unpublishedEvents;
        private final long sentEvents;
        private final long failedEvents;
        private final long deadEvents;
        private final long processingEvents;

        public long getTotalEvents() {
            return unpublishedEvents + sentEvents + failedEvents + deadEvents + processingEvents;
        }
    }
}
