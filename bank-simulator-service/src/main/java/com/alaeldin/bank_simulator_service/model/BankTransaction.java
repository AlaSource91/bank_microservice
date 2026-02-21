package com.alaeldin.bank_simulator_service.model;

import com.alaeldin.bank_simulator_service.constant.StatusTransaction;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing a bank transaction.
 * This entity stores information about fund transfers between bank accounts,
 * including source and destination accounts, amounts, and transaction status.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bank_transaction")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class BankTransaction {

    /**
     * The unique identifier for the transaction.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The unique reference ID for the transaction.
     * Used to identify transactions externally and must be unique across all transactions.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String referenceId;

    /**
     * The source bank account from which funds will be transferred.
     * References the BankAccount entity with foreign key constraint.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private BankAccount sourceAccount;

    /**
     * The destination bank account to which funds will be transferred.
     * References the BankAccount entity with foreign key constraint.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private BankAccount destinationAccount;

    /**
     * The amount of funds to be transferred.
     * Stored with precision of 19 digits and 2 decimal places.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * The description or reason for the transaction.
     */
    @Column(nullable = false, length = 500)
    private String description;

    /**
     * The current status of the transaction.
     * Possible values: PENDING, PROCESSING, COMPLETED, FAILED, REVERSED, or TIMED_OUT.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTransaction status;

    /**
     * The error code if the transaction failed.
     * May be null if the transaction succeeded.
     */
    @Column(length = 50)
    private String errorCode;

    /**
     * The error message if the transaction failed.
     * May be null if the transaction succeeded.
     */
    @Column(length = 500)
    private String errorMessage;

    /**
     * The timestamp when the transaction was executed.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime transactionDate;

    /**
     * The timestamp when the transaction record was created.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * The timestamp when the transaction was completed successfully.
     * Null if transaction has not been completed yet.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * The timestamp when the transaction failed.
     * Null if transaction has not failed.
     */
    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /**
     * The timestamp when the transaction record was last updated.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}
