package com.alaeldin.account_service.model;

import com.alaeldin.account_service.constant.AccountStatus;
import com.alaeldin.account_service.constant.OutBoxStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OutboxEvent
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "aggregate_id" , nullable = false)
    private String aggregateId;
    @Column(name = "aggregate_type" ,nullable = false)
    private String aggregateType;
    @Column(name = "event_type" ,  nullable = false )
    private String eventType;
    @Column(name = "event_payload" ,  columnDefinition = "TEXT", nullable = false)
    private String eventPayload;
    @Column(name = "idempotency_key" , nullable = false , unique = true)
    private String idempotencyKey;
    @Column(name = "created_at" , nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutBoxStatus status; //Pending , SENT , FAILED, PROCESSING , DEAD
    @Column(name = "retry_count" , nullable = false)
    @Builder.Default
    private int retryCount = 0;
    @Column(name = "max_retries",nullable = false)
    @Builder.Default
    private Integer maxRetries = 3; //max retry attempt  before giving up
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt; //Time Stamp for next  Retry attempt
    private String errorMessage;
    @Version
    @Column(name = "version")
    private Long version;


}
