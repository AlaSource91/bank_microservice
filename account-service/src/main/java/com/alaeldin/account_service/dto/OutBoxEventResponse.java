package com.alaeldin.account_service.dto;

import com.alaeldin.account_service.constant.AccountStatus;
import com.alaeldin.account_service.constant.OutBoxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
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
    private String eventPayload;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private OutBoxStatus status; //Pending , SENT , FAILED, PROCESSING , DEAD
    private int retryCount = 0;
    private Integer maxRetries = 3; //max retry attempt  before giving up
    private LocalDateTime nextRetryAt; //Time Stamp for next  Retry attempt
    private String errorMessage;
    private Long version;
}
