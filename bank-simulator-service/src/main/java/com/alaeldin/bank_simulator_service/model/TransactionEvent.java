package com.alaeldin.bank_simulator_service.model;

import com.alaeldin.bank_simulator_service.constant.StatusTransaction;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Immutable
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent
{
    private String eventId;
    private String transactionId;
    private String eventType;
    private StatusTransaction statusTransaction;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String description;
    private String idempotencyKey;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;


    private String applicationName;
    private String version;
}
