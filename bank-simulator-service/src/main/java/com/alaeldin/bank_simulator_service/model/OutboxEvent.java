package com.alaeldin.bank_simulator_service.model;

import com.alaeldin.bank_simulator_service.constant.OutBoxStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.lang.model.element.Name;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "event_payload", columnDefinition = "TEXT", nullable = false)
    private String eventPayload;//json Payload
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey; // Unique key to prevent duplicates
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // Timestamp for event creation
    @Column(name = "published_at")
    private LocalDateTime publishedAt; // Timestamp for when event was published
   @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutBoxStatus status; // PENDING, SENT, FAILED, PROCESSING, DEAD
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0; // Number of publish attempts
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private int maxRetries = 3; // Max retry attempts before giving up
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt; // Timestamp for next retry attempt
    private String errorMessage; // Optional field to store error details for failed events
    @Version
    private Long version; // For optimistic locking

}
