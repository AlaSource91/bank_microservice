package com.alaeldin.bank_query_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service responsible for managing idempotency of event processing using Redis.
 * Implements distributed locking to prevent duplicate event processing across multiple instances.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String REDIS_KEY_PREFIX = "event:processed:";
    private static final String STATE_PROCESSING = "processing";
    private static final String STATE_PROCESSED = "processed";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.consumer.idempotency.processing-ttl-seconds:60}")
    private int processingTtlSeconds;

    @Value("${app.consumer.idempotency.processed-ttl-minutes:10}")
    private int processedTtlMinutes;

    /**
     * Checks the idempotency state of an event.
     *
     * @param eventId the unique event identifier
     * @return IdempotencyState indicating the current state
     */
    public IdempotencyState checkState(String eventId) {
        try {
            String key = buildRedisKey(eventId);
            String state = redisTemplate.opsForValue().get(key);

            if (STATE_PROCESSED.equals(state)) {
                log.debug("Event already processed - EventId: {}", eventId);
                return IdempotencyState.ALREADY_PROCESSED;
            }

            if (STATE_PROCESSING.equals(state)) {
                log.debug("Event currently processing or recovering - EventId: {}", eventId);
                return IdempotencyState.PROCESSING;
            }

            // Try to acquire processing lock
            return acquireProcessingLock(eventId, key);

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failed during idempotency check for EventId: {}. " +
                    "Proceeding with processing (degraded mode).", eventId, e);
            return IdempotencyState.NEW;

        } catch (Exception e) {
            log.error("Unexpected error during idempotency check for EventId: {}. " +
                    "Proceeding with processing.", eventId, e);
            return IdempotencyState.NEW;
        }
    }

    /**
     * Marks an event as successfully processed in Redis.
     *
     * @param eventId the unique event identifier
     */
    public void markAsProcessed(String eventId) {
        try {
            String key = buildRedisKey(eventId);
            redisTemplate.opsForValue()
                    .set(key, STATE_PROCESSED, processedTtlMinutes, TimeUnit.MINUTES);
            log.debug("Event marked as processed in Redis - EventId: {}, TTL: {}min",
                    eventId, processedTtlMinutes);

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis connection failed when marking event as processed: {}. " +
                    "Event was processed but not cached.", eventId, e);

        } catch (Exception e) {
            log.error("Unexpected error when marking event as processed in Redis: {}",
                    eventId, e);
        }
    }

    /**
     * Attempts to acquire a processing lock for an event.
     *
     * @param eventId the unique event identifier
     * @param key the Redis key
     * @return IdempotencyState.NEW if lock acquired, IdempotencyState.PROCESSING otherwise
     */
    private IdempotencyState acquireProcessingLock(String eventId, String key) {
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(key, STATE_PROCESSING, processingTtlSeconds, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(lockAcquired)) {
            log.debug("Processing lock acquired - EventId: {}, TTL: {}s",
                    eventId, processingTtlSeconds);
            return IdempotencyState.NEW;
        } else {
            log.debug("Failed to acquire lock - EventId: {}", eventId);
            return IdempotencyState.PROCESSING;
        }
    }

    /**
     * Builds Redis key for event tracking.
     *
     * @param eventId the event ID
     * @return Redis key
     */
    private String buildRedisKey(String eventId) {
        return REDIS_KEY_PREFIX + eventId;
    }

    /**
     * Represents the idempotency state of an event.
     */
    public enum IdempotencyState {
        /** Event is new and should be processed */
        NEW,

        /** Event is currently being processed or recovering from crash (skip for now) */
        PROCESSING,

        /** Event was already processed successfully (skip, it's a duplicate) */
        ALREADY_PROCESSED
    }
}

