package com.alaeldin.bank_simulator_service.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Immutable
@NoArgsConstructor
@AllArgsConstructor
public class SagaEvent {

    private String eventId;
    private String sagaId;
    private String transactionReferenceId;
    private String eventType;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private String failureReason;
    private String currentStep;
    private String sagaStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    private String applicationName;

}
