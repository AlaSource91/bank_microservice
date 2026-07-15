package com.alaeldin.transaction_service.model;

import com.alaeldin.transaction_service.constant.StatusTransaction;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.*;

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
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class ,property = "id")
public class BankTransaction {

    /**
     * The unique identifier for the Transaction
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The unique reference ID for the transaction
     * Used to identity transaction externally and must be unique across all Transactions
     */
    @Column(nullable = false , unique = true , length = 50)
    private String reference;

    /**
     * The Source Bank Account from which funds will be transferred
     */
    @Column(name = "source_account_number", nullable = false)
    private String sourceAccountNumber;

    /**
     * The Destination BankAccount will be Transferred
     */
    @Column(name = "destination_account_number")
    private String destinationAccountNumber;

    /**
     * The Amount of fund be transferred.
     * Stored with precision of 19 and 2 decimal places
     */
    @Column(nullable = false , precision = 19 , scale = 2)
    private BigDecimal amount;

    /**
     * The Description or reason for the transaction
     */
    @Column(nullable = false, length = 500)
    private String description;

    /**
     * The Current status of the transaction.
     * Possible values : PENDING , PROCESSING
     * , COMPLETED , FAILED, REVERSED , OR TIME_OUT
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTransaction status;

    /**
     * The Error Code if the Transaction failed
     * May be null if the transaction succeeded.
     */
    @Column(length = 50)
    private String errorCode;

    /**
     * The error message if the transaction failed
     * May be null if the transaction succeeded
     */
    @Column(length = 500)
    private String errorMessage;

    /**
     * The TimeStamp when the transaction was executed
     */
    @Column(nullable = false , updatable = false)
    private LocalDateTime transactionDate;

    /**
     * The timeStamp when the transaction record was created.
     */
    @Column(nullable = false , updatable = false)
    private LocalDateTime creationDate;

    /**
     * The timeStamp when the transaction was completed Successfully
     * Null if Transaction has been completed yet
     */
    @Column(name = "completed_at")
    private LocalDateTime completedDate;

    @Column(name = "failed_at")
    private LocalDateTime failedDate;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
