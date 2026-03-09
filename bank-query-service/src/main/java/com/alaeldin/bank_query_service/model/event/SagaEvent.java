package com.alaeldin.bank_query_service.model.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SagaEvent extends BaseEvent {

    /** Matches {@code SagaEvent.sagaId} from bank-simulator-service. */
    private String sagaId;

    /** The business transaction reference this saga is orchestrating. */
    private String transactionReferenceId;

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


