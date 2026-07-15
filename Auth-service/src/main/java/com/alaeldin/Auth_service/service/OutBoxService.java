package com.alaeldin.Auth_service.service;

import com.alaeldin.Auth_service.constant.OutBoxStatus;
import com.alaeldin.Auth_service.dto.OutboxEventRequest;
import com.alaeldin.Auth_service.exception.ResourceNotFoundException;
import com.alaeldin.Auth_service.model.OutboxEvent;
import com.alaeldin.Auth_service.repository.OutBoxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for all outbox-table operations.
 *
 * <p>Outbox events move through the following state machine:</p>
 * <pre>
 *   PENDING ──► PROCESSING ──► SENT
 *      ▲              │
 *      └── FAILED ◄───┘  (retried up to maxRetries times)
 *                │
 *              DEAD  (when retryCount {@literal >=} maxRetries)
 * </pre>
 *
 * <p>All write methods are {@code @Transactional} so they participate in the
 * caller's transaction and roll back atomically on failure. The Redis cache is a
 * best-effort optimisation — its failure is logged but never propagated.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutBoxService {

    private final OutBoxEventRepository        outboxEventRepository;
    private final ObjectMapper                 objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    // ─────────────────────────────────────────────────────────────
    //  Save event to outbox
    // ─────────────────────────────────────────────────────────────

    /**
     * Persists an outbox event within the caller's transaction, guaranteeing
     * at-least-once delivery to Kafka.
     *
     * <p>Idempotent: if an event with the same {@code idempotencyKey} already exists
     * the existing record is returned without inserting a duplicate.</p>
     *
     * @param request the event to persist
     * @return the persisted (or existing) {@link OutboxEvent}
     */
    @Transactional
    public OutboxEvent saveEventToOutbox(OutboxEventRequest request) {
        try {
            validateOutboxEventRequest(request);

            return outboxEventRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .map(existing -> {
                        log.debug("Duplicate outbox event ignored: idempotencyKey={}, id={}, status={}",
                                request.getIdempotencyKey(), existing.getId(), existing.getStatus());
                        return existing;
                    })
                    .orElseGet(() -> createNewOutboxEvent(request));

        } catch (DataIntegrityViolationException e) {
            // Concurrent insertion race — another thread beat us to the INSERT
            log.debug("Concurrent outbox insert detected for idempotencyKey={} — fetching existing row",
                    request.getIdempotencyKey());
            return outboxEventRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> new RuntimeException(
                            "Concurrent outbox insert failed and existing row not found for key: "
                                    + request.getIdempotencyKey()));
        } catch (Exception e) {
            log.error("Failed to save outbox event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }

    private OutboxEvent createNewOutboxEvent(OutboxEventRequest request) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(request);
            OutboxEvent event = OutboxEvent.builder()
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
            OutboxEvent saved = outboxEventRepository.save(event);
            log.debug("Created outbox event: id={}, aggregateId={}, eventType={}, status={}",
                    saved.getId(), saved.getAggregateId(), saved.getEventType(), saved.getStatus());
            return saved;
        } catch (Exception e) {
            log.error("Failed to create outbox event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }

    private void validateOutboxEventRequest(OutboxEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("OutboxEventRequest must not be null");
        }
        if (request.getAggregateId() == null || request.getAggregateId().isBlank()) {
            throw new IllegalArgumentException("Aggregate ID must not be blank");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank");
        }
        if (request.getEventType() == null || request.getEventType().isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }
    }


    // ─────────────────────────────────────────────────────────────
    //  Status transitions
    // ─────────────────────────────────────────────────────────────

    /**
     * Marks the event as {@link OutBoxStatus#SENT} and caches the idempotency key
     * in Redis to fast-reject any late-arriving duplicate publishes.
     *
     * <p>The atomic UPDATE is tried first; a fallback direct save handles the
     * case where the status changed concurrently.</p>
     */
    @Transactional
    public void markEventAsPublished(Long eventId, String idempotencyKey) {
        try {
            boolean updated = atomicStatusUpdate(eventId, OutBoxStatus.PROCESSING, OutBoxStatus.SENT, LocalDateTime.now());
            if (!updated) {
                OutboxEvent event = findEventById(eventId);
                event.setStatus(OutBoxStatus.SENT);
                event.setPublishedAt(LocalDateTime.now());
                event.setErrorMessage(null);
                outboxEventRepository.save(event);
                log.warn("Marked outbox event as SENT via fallback: id={}, idempotencyKey={}", eventId, idempotencyKey);
            } else {
                log.debug("Marked outbox event as SENT: id={}, idempotencyKey={}", eventId, idempotencyKey);
            }
            // Best-effort Redis cache — must not roll back the DB transaction on failure
            cachePublishedEventInRedis(idempotencyKey);
        } catch (Exception ex) {
            log.error("Failed to mark outbox event as published: eventId={}, error={}", eventId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to mark outbox event as published", ex);
        }
    }

    /**
     * Increments the retry counter and either re-schedules the event as
     * {@link OutBoxStatus#PENDING} (exponential back-off) or moves it to
     * {@link OutBoxStatus#DEAD} when {@code maxRetries} is reached.
     */
    @Transactional
    public void markEventAsFailed(Long eventId, String errorMessage) {
        try {
            OutboxEvent event = findEventById(eventId);
            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessage(errorMessage);

            if (event.getRetryCount() >= event.getMaxRetries()) {
                event.setStatus(OutBoxStatus.DEAD);
                event.setNextRetryAt(null);
                outboxEventRepository.save(event);
                log.error("Outbox event exceeded max retries — moved to DEAD: id={}, retryCount={}, error={}",
                        eventId, event.getRetryCount(), errorMessage);
                return;
            }

            // Exponential back-off: 2^retryCount minutes (2 m, 4 m, 8 m, …)
            int delayMinutes = (int) Math.pow(2, event.getRetryCount());
            event.setStatus(OutBoxStatus.PENDING);
            event.setNextRetryAt(LocalDateTime.now().plusMinutes(delayMinutes));
            outboxEventRepository.save(event);
            log.warn("Outbox event scheduled for retry: id={}, retryCount={}, nextRetryAt={}, error={}",
                    eventId, event.getRetryCount(), event.getNextRetryAt(), errorMessage);
        } catch (Exception ex) {
            log.error("Failed to mark outbox event as failed: eventId={}, error={}", eventId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to mark outbox event as failed", ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Batch retrieval
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns up to {@code batchSize} events ready for publishing, using
     * {@code SELECT FOR UPDATE SKIP LOCKED} to prevent concurrent workers from
     * picking the same events.
     */
    @Transactional
    public List<OutboxEvent> lockBatchForPublishing(int batchSize) {
        return outboxEventRepository.lockBatchForPublishing(LocalDateTime.now(), batchSize);
    }

    /**
     * Bulk-marks a list of event IDs as {@link OutBoxStatus#PROCESSING}.
     *
     * @return number of rows updated
     */
    @Transactional
    public int markEventsAsProcessing(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return 0;
        }
        int updated = outboxEventRepository.markEventsAsProcessing(eventIds);
        log.info("Marked {} event(s) as PROCESSING", updated);
        return updated;
    }

    // ─────────────────────────────────────────────────────────────
    //  Stale event recovery
    // ─────────────────────────────────────────────────────────────

    /**
     * Recovers events stuck in {@link OutBoxStatus#PROCESSING} for longer than
     * {@code staleThresholdMinutes} minutes (e.g. due to a worker crash) back to
     * {@link OutBoxStatus#PENDING} so they can be retried.
     *
     * <p>All resets run in a single transaction — if any individual reset fails
     * the whole batch rolls back, preventing partial state corruption.</p>
     */
    @Transactional
    public List<OutboxEvent> findAndRecoverStaleEvents(int staleThresholdMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleThresholdMinutes);
        List<OutboxEvent> staleEvents = outboxEventRepository.findStaleProcessingEvents(threshold);

        if (!staleEvents.isEmpty()) {
            log.warn("Recovering {} stale PROCESSING event(s) back to PENDING", staleEvents.size());
            for (OutboxEvent event : staleEvents) {
                resetEventToPending(event, "Recovered from stale PROCESSING state");
            }
        }
        return staleEvents;
    }



    // ─────────────────────────────────────────────────────────────
    //  Atomic status helper
    // ─────────────────────────────────────────────────────────────

    /**
     * Conditional UPDATE: transitions status from {@code currentStatus} to {@code newStatus}
     * only when the row still carries the expected status.
     *
     * @return {@code true} if exactly one row was updated
     */
    public boolean atomicStatusUpdate(Long eventId, OutBoxStatus currentStatus,
                                      OutBoxStatus newStatus, LocalDateTime publishedAt) {
        try {
            int updated = outboxEventRepository.updateEventStatus(eventId, currentStatus, newStatus, publishedAt);
            if (updated > 0) {
                log.debug("Atomic status update: id={} {} → {}", eventId, currentStatus, newStatus);
                return true;
            }
            log.warn("Atomic status update had no effect: id={}, expected={}, new={}",
                    eventId, currentStatus, newStatus);
            return false;
        } catch (Exception ex) {
            log.error("Atomic status update failed: id={}, {} → {}, error={}",
                    eventId, currentStatus, newStatus, ex.getMessage(), ex);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Resets an already-loaded event to PENDING within the surrounding transaction.
     * Private to prevent accidental self-invocation via {@code this} (which bypasses
     * the Spring AOP proxy and would lose any {@code @Transactional} semantics).
     */
    private void resetEventToPending(OutboxEvent event, String reason) {
        try {
            event.setStatus(OutBoxStatus.PENDING);
            event.setErrorMessage(reason);
            outboxEventRepository.save(event);
            log.info("Reset stale event to PENDING: id={}", event.getId());
        } catch (Exception ex) {
            log.error("Failed to reset event to PENDING: id={}, error={}", event.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to reset event to PENDING", ex);
        }
    }

    private OutboxEvent findEventById(Long eventId) {
        return outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Outbox event not found: id=" + eventId));
    }

    /**
     * Best-effort Redis cache write. Redis is a cache, not the system of record —
     * its unavailability must never fail the outbox database operation.
     */
    private void cachePublishedEventInRedis(String idempotencyKey) {
        if (idempotencyKey == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(idempotencyKey, "published", Duration.ofHours(5));
            log.debug("Cached published event in Redis: idempotencyKey={}", idempotencyKey);
        } catch (Exception ex) {
            log.warn("Redis cache write failed (non-fatal) for idempotencyKey={}: {}",
                    idempotencyKey, ex.getMessage());
        }
    }
}


