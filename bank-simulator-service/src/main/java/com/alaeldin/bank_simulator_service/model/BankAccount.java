package com.alaeldin.bank_simulator_service.model;

import com.alaeldin.bank_simulator_service.constant.AccountStatus;
import com.alaeldin.bank_simulator_service.constant.AccountType;
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
 * Entity representing a bank account.
 * This entity stores information about customer bank accounts including balance,
 * account type, and account status.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bank_account")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class BankAccount {

    /**
     * The unique identifier for the bank account.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The unique account number.
     * Must be unique across all accounts in the system.
     */
    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;

    /**
     * The name of the account holder.
     */
    @Column(nullable = false, length = 100)
    private String accountHolderName;

    /**
     * The current balance of the account.
     * Stored with precision of 19 digits and 2 decimal places.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /**
     * The type of the account (PERSONAL or BUSINESS).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType accountType;

    /**
     * Version column for optimistic locking.
     * Automatically incremented by JPA on each update to detect concurrent modifications.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Identifier of the entity/process that currently holds a lock on this account.
     * Used for distributed locking, debugging, and audit purposes.
     */
    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    /**
     * Timestamp when the lock was acquired.
     * Locks expire after 5 minutes (configurable).
     */
    @Column(name = "lock_timestamp")
    private LocalDateTime lockTimestamp;
    /**
     * The current status of the account (ACTIVE, FROZEN, or CLOSED).
     * Defaults to ACTIVE when a new account is created.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE'", name = "status")
    private AccountStatus accountStatus;

    /**
     * The timestamp when the account was created.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * The timestamp when the account was last updated.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Checks if this account is currently locked.
     * A lock is valid if:
     * - Lock timestamp exists
     * - Lock was acquired within the last 5 minutes (not expired)
     *
     * @return true if locked and not expired, false otherwise
     */
    public boolean isLocked() {
        if (lockTimestamp == null || lockedBy == null) {
            return false;
        }
        return lockTimestamp.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    /**
     * Acquires a lock on this account with the specified owner identifier.
     *
     * @param owner the identifier of the process/entity acquiring the lock
     * @throws IllegalStateException if the account is already locked
     */
    public void acquireLock(String owner) {
        if (isLocked()) {
            throw new IllegalStateException(
                    String.format("Account %s is already locked by %s", accountNumber, lockedBy)
            );
        }
        this.lockedBy = owner;
        this.lockTimestamp = LocalDateTime.now();
    }

    /**
     * Releases the lock on this account.
     * Clears both the lock owner and timestamp.
     */
    public void releaseLock() {
        this.lockedBy = null;
        this.lockTimestamp = null;
    }

    /**
     * Checks if the lock has expired (older than 5 minutes).
     *
     * @return true if lock exists and has expired, false otherwise
     */
    public boolean isLockExpired() {
        if (lockTimestamp == null) {
            return false;
        }
        return lockTimestamp.isBefore(LocalDateTime.now().minusMinutes(5));
    }


}
