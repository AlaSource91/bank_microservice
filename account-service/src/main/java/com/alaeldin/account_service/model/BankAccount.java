package com.alaeldin.account_service.model;

import com.alaeldin.account_service.constant.AccountStatus;
import com.alaeldin.account_service.constant.AccountType;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true , length = 20)
    private String accountNumber;
    @Column(nullable = false,length = 20)
    private String accountHolderName;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE'", name = "status")
    private AccountStatus accountStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false , length = 20)
    private AccountType accountType;
    /**
     * Version column for optimistic locking.
     * Automatically incremented by JPA on each update to detect concurrent modifications.
     */
    @Version
    @Column(name = "version" , nullable = false)
    private Long version;
    /**
     * Identifier of the entity/process that currently holds a lock on this account.
     * Used for distributed locking, debugging, and audit purposes.
     */
    @Column(name = "locked_by", length = 20)
    private String lockBy;
    /**
     * Timestamp when the lock was acquired.
     * Locks expire after 5 minutes (configurable).
     */
    @Column(name = "lock_timestamp")
    private LocalDateTime lockTimestamp;
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false , updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     *  this function is checks if this account  is currently locked
     *  A is Valid if :
     *  Lock time stamp is exists
     *   Lock was acquired within the last 5 minutes (not expired)
     * @return true if locked and  not expired false otherwise
     */
    public boolean isLocked()
    {
        if (lockTimestamp == null && lockBy == null) {
            return false;
        }

        assert lockTimestamp != null;
        return lockTimestamp.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    public void acquireLock(String owner)
    {
        if (isLocked()) {
            throw new IllegalStateException(
                    String.format("Account %s is already locked by %s", accountNumber, lockBy)
            );
        }
        this.lockBy = owner;
        this.lockTimestamp = LocalDateTime.now();
    }

    public void releaseLock()
    {
        this.lockBy = null;
        this.lockTimestamp = null;
    }

    public boolean isExpiredLocked()
    {
        if (lockTimestamp == null) {
            return false;
        }
        return lockTimestamp.isBefore(LocalDateTime.now().minusMinutes(5));
    }
}
