package com.alaeldin.account_service.dto;

import com.alaeldin.account_service.constant.AccountStatus;
import com.alaeldin.account_service.constant.AccountType;
import com.alaeldin.account_service.model.BankAccount;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AccountResponse
{
    private String accountNumber;
    private String accountHolderName;
    private BigDecimal balance;
    private AccountType accountType;
    private AccountStatus accountStatus;
    private Long userId;
    private LocalDateTime createdAt;

}
