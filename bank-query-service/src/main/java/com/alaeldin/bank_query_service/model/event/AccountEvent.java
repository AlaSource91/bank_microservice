package com.alaeldin.bank_query_service.model.event;

import com.alaeldin.bank_query_service.constant.AccountStatus;
import com.alaeldin.bank_query_service.constant.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountEvent extends BaseEvent {


    private String accountHolderName;
    private AccountType accountType;  // FIXED: Changed from AccountEventType to AccountType
    private BigDecimal balance;
    private AccountStatus status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    private String applicationName;
    private String version;
}
