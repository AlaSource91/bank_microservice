package com.alaeldin.bank_simulator_service.dto;

import com.alaeldin.bank_simulator_service.constant.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Data Transfer Object for creating a new bank account.
 * This DTO is used to transfer account creation request data from clients.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountRequest {


    @NotBlank(message = "Account holder name cannot be blank")
    @Size(min = 2, max = 100, message = "Account holder name must be between 2 and 100 characters")
    private String accountHolderName;

    /**
     * The initial balance of the account.
     * Must be a positive number (greater than or equal to 0).
     */
    @NotNull(message = "Balance cannot be null")
    @DecimalMin(value = "0.0", message = "Balance must be greater than or equal to 0")
    private BigDecimal balance;

    /**
     * The type of the account (PERSONAL or BUSINESS).
     * Must not be null.
     */
    @NotNull(message = "Account type cannot be null")
    private AccountType accountType;

    @NotNull(message = "Status cannot be null")
    private String status;
}
