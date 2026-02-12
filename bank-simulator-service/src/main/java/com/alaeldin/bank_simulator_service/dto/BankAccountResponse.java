package com.alaeldin.bank_simulator_service.dto;

import com.alaeldin.bank_simulator_service.constant.AccountStatus;
import com.alaeldin.bank_simulator_service.constant.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for bank account response.
 * This DTO is used to transfer account information back to clients after account operations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountResponse {

    /**
     * The unique account number.
     */
    private String accountNumber;

    /**
     * The name of the account holder.
     */
    private String accountHolderName;

    /**
     * The current balance of the account.
     */
    private BigDecimal balance;

    /**
     * The type of the account (PERSONAL or BUSINESS).
     */
    private AccountType accountType;

    /**
     * The current status of the account (ACTIVE, FROZEN, or CLOSED).
     */
    private AccountStatus accountStatus;

    /**
     * The timestamp when the account was created.
     */
    private LocalDateTime createdAt;

    /**
     * The timestamp when the account was last updated.
     */
    private LocalDateTime updatedAt;
}
