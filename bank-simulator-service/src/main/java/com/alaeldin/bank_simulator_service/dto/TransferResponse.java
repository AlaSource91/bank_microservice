package com.alaeldin.bank_simulator_service.dto;

import com.alaeldin.bank_simulator_service.constant.StatusTransaction;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for bank transaction response.
 * This DTO is used to transfer transaction information back to clients after transaction operations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {

    /**
     * The unique reference ID for the transaction.
     */
    private String referenceId;

    /**
     * The source bank account from which funds were transferred.
     */
    private BankAccount sourceAccount;

    /**
     * The destination bank account to which funds were transferred.
     */
    private BankAccount destinationAccount;

    /**
     * The amount of funds transferred.
     */
    private BigDecimal amount;

    /**
     * The description or reason for the transaction.
     */
    private String description;

    /**
     * The current status of the transaction (PENDING, PROCESSING, COMPLETED, FAILED, REVERSED, or TIMED_OUT).
     */
    private StatusTransaction status;

    /**
     * The error code if the transaction failed.
     * May be null if the transaction succeeded.
     */
    private String errorCode;

    /**
     * The error message if the transaction failed.
     * May be null if the transaction succeeded.
     */
    private String errorMessage;

    /**
     * The timestamp when the transaction was executed.
     */
    private LocalDateTime transactionDate;
    /**
     * The timestamp when the transaction was completed.
     * May be null if the transaction is still pending or processing.
     */
    private LocalDateTime completedAt;
    /**
     * The timestamp when the transaction failed.
     * May be null if the transaction succeeded or is still pending/processing.
     */
    private LocalDateTime failedAt;
    /**
     * The timestamp when the transaction record was created.
     */
    private LocalDateTime createdAt;

    /**
     * The timestamp when the transaction record was last updated.
     */
    private LocalDateTime updatedAt;
}
