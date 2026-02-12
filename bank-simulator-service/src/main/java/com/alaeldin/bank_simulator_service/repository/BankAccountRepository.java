package com.alaeldin.bank_simulator_service.repository;

import com.alaeldin.bank_simulator_service.constant.AccountStatus;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BankAccount entity.
 * Provides database access operations for bank account management.
 * Extends JpaRepository to inherit standard CRUD operations and query capabilities.
 *
 * <p>Custom query methods:</p>
 * <ul>
 *   <li>findByAccountNumber - Retrieve a specific account by its unique account number</li>
 *   <li>findByAccountStatus - Retrieve all accounts with a specific status</li>
 * </ul>
 *
 * @see com.alaeldin.bank_simulator_service.model.BankAccount
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    /**
     * Retrieves a bank account by its unique account number.
     * Account numbers are unique across the system, so at most one account will be returned.
     *
     * @param accountNumber the unique account number to search for (e.g., "ACC123456")
     * @return an Optional containing the BankAccount if found, or empty if not found
     */
    Optional<BankAccount> findByAccountNumber(String accountNumber);

    /**
     * Retrieves all bank accounts with a specific account status with pagination support.
     * Multiple accounts can have the same status, so this returns a paginated result set.
     *
     * @param accountStatus the status to filter by (e.g., ACTIVE, FROZEN, CLOSED)
     * @param pageable      the pagination information (page number, size, sorting)
     * @return a Page of BankAccounts with the specified status. Returns an empty page if no accounts found.
     */
    Page<BankAccount> findByAccountStatus(AccountStatus accountStatus, Pageable pageable);

    /**
     * Checks if an account with the specified holder name already exists.
     * Used for duplicate detection before creating a new account.
     *
     * @param accountHolderName the account holder name to check for existence
     * @return true if an account with this holder name exists, false otherwise
     */
    boolean existsByAccountHolderName(String accountHolderName);

    Page<BankAccount> findByAccountHolderName(String accountHolderName, Pageable pageable);

    @Query("SELECT b.balance FROM BankAccount b  WHERE b.accountNumber = :accountNumber")
    BigDecimal findBalanceByAccountNumber(String accountNumber);

    /**
     * Retrieves a bank account with optimistic locking.
     * Forces version increment on every read (for update operations).
     * <p>
     * Use this method when you plan to update the account.
     * JPA will automatically check version on save.
     *
     * @param accountNumber the account number to find
     * @return Optional containing the account with lock, or empty
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT ba FROM BankAccount ba WHERE ba.accountNumber = :accountNumber")
    Optional<BankAccount> findByAccountNumberWithLock(@Param("accountNumber") String accountNumber);

    /**
     * Retrieves only the version number of an account.
     * Lightweight query for cache population - does not load entire entity.
     * <p>
     * Perfect for caching: only fetches what we need.
     *
     * @param accountNumber the account number
     * @return Optional containing the version number, or empty
     */
    @Query("SELECT ba.version FROM BankAccount ba WHERE ba.accountNumber = :accountNumber")
    Optional<Long> getVersion(@Param("accountNumber") String accountNumber);

    @Query("SELECT ba FROM BankAccount ba WHERE ba.lockedBy IS NOT NULL AND ba.lockTimestamp > :cutoffTime")
    List<BankAccount> findLockedAccounts(@Param("cutoffTime") java.time.LocalDateTime cutoffTime);
}


