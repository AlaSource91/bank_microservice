package com.alaeldin.bank_simulator_service.model;

import com.alaeldin.bank_simulator_service.constant.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.errorprone.annotations.Immutable;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@Getter
@Immutable
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountEvent
{

    private String eventId;
    private String accountNumber;
    private String accountHolderName;
    private AccountType accountType;
    private String eventType;
    private BigDecimal balance;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    private String applicationName;
    private String version;

}
