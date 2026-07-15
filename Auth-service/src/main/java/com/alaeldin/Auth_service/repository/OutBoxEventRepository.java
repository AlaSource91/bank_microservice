package com.alaeldin.Auth_service.repository;

import com.alaeldin.Auth_service.constant.OutBoxStatus;
import com.alaeldin.Auth_service.model.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutBoxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE (o.status = com.alaeldin.Auth_service.constant.OutBoxStatus.PENDING
               OR o.status = com.alaeldin.Auth_service.constant.OutBoxStatus.FAILED)
        AND o.retryCount < o.maxRetries
        AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :currentTime)
        ORDER BY o.createdAt ASC
        """)
    List<OutboxEvent> findEventsReadyForPublishing(@Param("currentTime") LocalDateTime currentTime, Pageable pageable);

    default List<OutboxEvent> findEventsReadyForPublishing(LocalDateTime currentTime, int limit) {
        return findEventsReadyForPublishing(currentTime, Pageable.ofSize(limit));
    }

    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = com.alaeldin.Auth_service.constant.OutBoxStatus.DEAD
           OR (o.status = com.alaeldin.Auth_service.constant.OutBoxStatus.FAILED
               AND o.retryCount >= o.maxRetries)
        ORDER BY o.createdAt ASC
        """)
    List<OutboxEvent> findDeadOrExhaustedEvents();

    long countByStatus(OutBoxStatus status);

    @Query("""
        SELECT COUNT(o) FROM OutboxEvent o
        WHERE o.status IN (
            com.alaeldin.Auth_service.constant.OutBoxStatus.PENDING,
            com.alaeldin.Auth_service.constant.OutBoxStatus.FAILED,
            com.alaeldin.Auth_service.constant.OutBoxStatus.PROCESSING
        )
        """)
    long countUnpublishedEvents();

    List<OutboxEvent> findByAggregateIdAndAggregateTypeOrderByCreatedAtDesc(String aggregateId, String aggregateType);

    @Transactional
    @Modifying
    @Query("""
        DELETE FROM OutboxEvent o
        WHERE o.status = com.alaeldin.Auth_service.constant.OutBoxStatus.SENT
        AND o.publishedAt < :cutoffTime
        """)
    void deleteOldPublishedEvents(@Param("cutoffTime") LocalDateTime cutoffTime);

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

    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = com.alaeldin.Auth_service.constant.OutBoxStatus.PROCESSING
        AND o.createdAt < :staleThreshold
        ORDER BY o.createdAt ASC
        """)
    List<OutboxEvent> findStaleProcessingEvents(@Param("staleThreshold") LocalDateTime staleThreshold);

    @Transactional
    @Modifying
    @Query("""
        UPDATE OutboxEvent o
        SET o.status = :newStatus, o.publishedAt = :publishedAt
        WHERE o.id = :eventId AND o.status = :currentStatus
        """)
    int updateEventStatus(@Param("eventId") Long eventId,
                          @Param("currentStatus") OutBoxStatus currentStatus,
                          @Param("newStatus") OutBoxStatus newStatus,
                          @Param("publishedAt") LocalDateTime publishedAt);

    @Transactional
    @Modifying
    @Query("""
        UPDATE OutboxEvent o
        SET o.status = com.alaeldin.Auth_service.constant.OutBoxStatus.PROCESSING
        WHERE o.id IN :eventIds
        AND o.status = com.alaeldin.Auth_service.constant.OutBoxStatus.PENDING
        """)
    int markEventsAsProcessing(@Param("eventIds") List<Long> eventIds);
}
