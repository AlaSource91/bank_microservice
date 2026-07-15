package com.alaeldin.account_service.model;

import com.alaeldin.account_service.constant.AccountStatus;
import com.alaeldin.account_service.constant.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@Getter
@Immutable
@NoArgsConstructor
@AllArgsConstructor
public class AccountEvent
{

    private String accountNumber;
    private String accountHolderName;
    private AccountType accountType;
    private String eventId;
    private String eventType;
    private BigDecimal balance;
    private AccountStatus accountStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    private String applicationName;
    private String version;

}
