package com.alaeldin.account_service.repository;

import com.alaeldin.account_service.constant.OutBoxStatus;import com.alaeldin.account_service.model.OutboxEvent;
import org.springframework.data.domain.PageRequest;import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.Modifying;import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;import java.time.LocalDateTime;import java.util.List;import java.util.Optional;

public interface OutBoxEventRepository extends JpaRepository<OutboxEvent, Long> {


    /**
     * Find By idempotency for Duplication
     */
    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
 SELECT o FROM OutboxEvent o 
 WHERE (
        o.status = com.alaeldin.account_service.constant.OutBoxStatus.PENDING
        OR (
            o.status = com.alaeldin.account_service.constant.OutBoxStatus.FAILED
            AND o.retryCount < o.maxRetries
            AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :currentTime)
        )
 )
 ORDER BY o.createdAt ASC
""")
    List<OutboxEvent> findEventsReadyForPublishing(
            @Param("currentTime") LocalDateTime currentTime,
            Pageable pageable
    );

    /**
     *  Convenience method to find events ready for publishing with limit
     */
    default List<OutboxEvent> findEventsReadyForPublishing(LocalDateTime currentTime, int limit) {
        return findEventsReadyForPublishing(currentTime, PageRequest.of(0, limit));
    }

    /**
     * Find Events That Failed and exceeded max retries (Dead Events)
     */

    @Query(value = """ 
       SELECT o FROM OutboxEvent o 
              WHERE o.status = com.alaeldin.account_service.constant.OutBoxStatus.DEAD
                  OR(o.status = com.alaeldin.account_service.constant.OutBoxStatus.FAILED
                          AND o.retryCount >= o.maxRetries)
                                 ORDER BY o.createdAt Asc   
              """)

    List<OutboxEvent> findFailedEvents();

    /**
     * Count Events By Specific Status For Monitoring
     */

    Long countByStatus(OutBoxStatus status);

    /**
     * Count unpublish events(PENDING , FAILED , PROCESSING)
     */
    @Query(value = """
    SELECT COUNT(o) FROM OutboxEvent o
        WHERE o.status IN (
            com.alaeldin.account_service.constant.OutBoxStatus.PENDING,
                com.alaeldin.account_service.constant.OutBoxStatus.FAILED ,
                com.alaeldin.account_service.constant.OutBoxStatus.PROCESSING
            )
    """
)
    Long countUnpublishedEvents();
    /*
     ** Find events by aggregate ID and type for debugging and tracking
     */
    List<OutboxEvent> findByAggregateIdAndAggregateTypeOrderByCreatedAtDesc(String aggregateId, String aggregateType);

    /**
     * Clean Old Successfully sent events
     */

    /**
     * Find Events That are stuck  in PROCESSING State (potential zombie processes)
     * UseFull for recovery scenarios
     */
    @Modifying
    @Query(value = """
      DELETE FROM OutboxEvent o 
          WHERE o.status = com.alaeldin.account_service.constant.OutBoxStatus.SENT
              AND o.publishedAt < :cutoffTime
    """)
    void deleteOldPublishedEvents(@Param("cutoffTime")  LocalDateTime cutoffTime);

    @Query(value = """
    SELECT o FROM OutboxEvent o
    WHERE o.status = com.alaeldin.account_service.constant.OutBoxStatus.PROCESSING
    AND o.createdAt < :staleThreshold
    AND o.createdAt > :cutoffTime
    ORDER BY o.createdAt ASC
""")

    List<OutboxEvent> findStaleProcessingEvents(@Param("staleThreshold")
                                                LocalDateTime staleThreshold, @Param("cutoffTime")  LocalDateTime cutoffTime);

    /**
     * Update Event status atomically
     */
    @Modifying
    @Query(value = """
   UPDATE OutboxEvent o 
   SET o.status = :newStatus , o.publishedAt = :publishedAt
   WHERE o.id = :eventId AND o.status = :currentStatus
""")
    int updateEventStatus(
            @Param("eventId") long eventId,
            @Param("currentStatus") OutBoxStatus outBoxStatus,
            @Param("newStatus") OutBoxStatus newStatus ,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    /**
     * Batch update events to PROCESSING status to prevent  concurrent processing
     */
    @Modifying
    @Query("""
    UPDATE OutboxEvent o 
    SET o.status = com.alaeldin.account_service.constant.OutBoxStatus.PROCESSING
    WHERE o.id IN :eventIds
    AND o.status = com.alaeldin.account_service.constant.OutBoxStatus.PENDING
""")

    int markEventsAsProcessing(@Param("eventIds")List<Long> eventIds);

    /**
     * Lock a batch of events for publishing using SELECT FOR UPDATE SKIP LOCKED
     * This ensures only one publisher instance processes each event (distributed lock)
     * Uses native SQL for database-specific locking features
     * Includes both PENDING and FAILED events that are ready for retry
     */
    @Query(value = """
        SELECT * FROM outbox_events
        WHERE (status = 'PENDING' OR status = 'FAILED')
        AND retry_count < max_retries
        AND (next_retry_at IS NULL OR next_retry_at <= :now)
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> lockBatchForPublishing(@Param("now") LocalDateTime now,
                                             @Param("batchSize") int batchSize);
}

