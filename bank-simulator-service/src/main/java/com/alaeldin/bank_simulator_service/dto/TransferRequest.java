package com.alaeldin.bank_simulator_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Data Transfer Object for creating a new bank transaction.
 * This DTO is used to transfer transaction request data from clients.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {

    /**
     * The account number of the source bank account from which the funds will be transferred.
     * Must not be blank.
     */
    @NotBlank(message = "Source account number cannot be blank")
    private String sourceAccountNumber;

    /**
     * The account number of the destination bank account to which the funds will be transferred.
     * Must not be blank.
     */
    @NotBlank(message = "Destination account number cannot be blank")
    private String destinationAccountNumber;

    /**
     * The amount of funds to be transferred.
     * Must be a positive decimal number greater than 0.
     */
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    /**
     * The description or reason for the transaction.
     * Must not be blank.
     */
    @NotBlank(message = "Description cannot be blank")
    private String description;
}
