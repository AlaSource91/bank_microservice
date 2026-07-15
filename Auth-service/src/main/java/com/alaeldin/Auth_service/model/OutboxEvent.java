package com.alaeldin.Auth_service.model;

import com.alaeldin.Auth_service.constant.OutBoxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity representing a single outbox event that must be published to Kafka.
 *
 * <p>The <em>transactional outbox pattern</em> guarantees at-least-once delivery:
 * events are written to this table within the same database transaction as the
 * domain operation that triggered them. A separate polling job
 * ({@link com.alaeldin.Auth_service.job.OutboxPublisher}) reads PENDING rows,
 * publishes them to Kafka, and marks them {@link OutBoxStatus#SENT}.</p>
 *
 * <p><strong>Note on Lombok:</strong> {@code @Getter} and {@code @Setter} are used
 * instead of {@code @Data} to avoid generating {@code equals}/{@code hashCode}
 * methods based on all fields, which causes subtle bugs with JPA dirty-checking and
 * {@code HashSet} collections.</p>
 *
 * @see com.alaeldin.Auth_service.service.OutBoxService
 * @see com.alaeldin.Auth_service.job.OutboxPublisher
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    /** JSON-serialised event payload. Stored as TEXT to support large payloads. */
    @Column(name = "event_payload", columnDefinition = "TEXT", nullable = false)
    private String eventPayload;

    /** Unique key used to prevent duplicate inserts on retry. */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /** Set by the publisher once the event is successfully delivered to Kafka. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutBoxStatus status;

    /** Number of publish attempts made so far. Starts at 0. */
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /** Maximum number of attempts before the event is moved to {@link OutBoxStatus#DEAD}. */
    @Builder.Default
    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    /** Earliest UTC time at which the next retry attempt may be made. {@code null} = retry immediately. */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** Human-readable error from the last failed attempt. {@code null} when no failure has occurred. */
    @Column(name = "error_message")
    private String errorMessage;

    /** Optimistic-locking version counter — prevents silent concurrent overwrites. */
    @Version
    private Long version;
}
