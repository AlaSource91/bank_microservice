package com.alaeldin.bank_simulator_service.model;

import com.alaeldin.bank_simulator_service.constant.SagaStatus;
import com.alaeldin.bank_simulator_service.constant.SagaStep;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_state")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SagaState {

    @Id
    @Column(name = "saga_id", length = 50, nullable = false)
    private String sagaId;
    @Column(name = "transaction_reference_id", length = 50, nullable = false)
    private String transactionReferenceId;

    @Column(name = "status", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private SagaStatus status;
    @Column(name = "current_step", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private SagaStep currentStep;
    @Column(name = "source_account_number", length = 20, nullable = false)
    private String sourceAccountNumber;
    @Column(name = "destination_account_number", length = 20, nullable = false)
    private String destinationAccountNumber;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;
    @Column(name = "max_retries", nullable = false)
    private int maxRetries;
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Version
    @Column(nullable = false)
    private long version;

    public boolean isCompleted()
    {
        return status == SagaStatus.COMPLETED;
    }

    public boolean isFailed()
    {
        return  status == SagaStatus.FAILED || status == SagaStatus.COMPENSATED;
    }

    public boolean canRetry()
    {
        return retryCount < maxRetries;
    }
    public boolean isCompensationRequired()
    {
        return status == SagaStatus.COMPENSATED;
    }
   public boolean isStale(int thresholdMinutes)
   {
       return updatedAt != null && updatedAt.isBefore(LocalDateTime.now().minusMinutes(thresholdMinutes));

   }



}
