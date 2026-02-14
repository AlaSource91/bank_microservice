package com.alaeldin.bank_simulator_service.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutBoxEventResponse {


    private Long id;
    private String aggregateId;
    private String aggregateType;
    private String eventType;
    private String eventPayload;//json Payload
    private String idempotencyKey; // Unique key to prevent duplicates
    private LocalDateTime createdAt; // Timestamp for event creation
    private LocalDateTime publishedAt; // Timestamp for when event was published
    private boolean isPublished; // Flag to indicate if event has been published
    private int retryCount = 0; // Number of publish attempts
    private int maxRetries = 3; // Max retry attempts before giving up
    private LocalDateTime nextRetryAt; // Timestamp for next retry attempt
    private Long version; // For optimistic locking
   private String  errorMessage;
}
