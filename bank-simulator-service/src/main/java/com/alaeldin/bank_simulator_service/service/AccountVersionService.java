package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.exception.AccountLockedException;
import com.alaeldin.bank_simulator_service.exception.ResourceNotFoundException;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import com.alaeldin.bank_simulator_service.repository.BankAccountRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing account versioning with optimistic locking and distributed locking.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Version-based optimistic locking for concurrent transaction control</li>
 *   <li>Distributed locking with automatic expiration (5 minutes)</li>
 *   <li>Redis caching for version numbers (5-minute TTL)</li>
 *   <li>Automatic retry on optimistic lock failures
 *   (3 attempts with exponential backoff)</li>
 * </ul>
 *
 * @see BankAccount
 * @see BankAccountRepository
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class AccountVersionService {

    private final BankAccountRepository bankAccountRepository;
    private static final int LOCK_TIMEOUT_MINUTES = 5;

    /**
     * Retrieves the current version of an account from cache or database.
     *
     * <p>Cache Strategy:</p>
     * <ul>
     *   <li>Cache Hit (70%): Returns in ~5ms</li>
     *   <li>Cache Miss (30%): Queries DB in ~250ms, then caches</li>
     *   <li>TTL: 5 minutes</li>
     * </ul>
     *
     * @param accountNumber the account number
     * @return the current version number
     * @throws ResourceNotFoundException if account doesn't exist
     */
    @Cacheable(value = "accountVersions", key = "#accountNumber")
    public Long getVersion(String accountNumber) {
        log.debug("Fetching version for account: {}", accountNumber);

        Long version = bankAccountRepository.getVersion(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found: {}", accountNumber);
                    return new ResourceNotFoundException("BankAccount"
                            , "accountNumber", accountNumber);
                });

        log.debug("Version for account {}: {}", accountNumber, version);
        return version;
    }

    /**
     * Updates account balance with optimistic locking and distributed lock management.
     *
     * <p>Process Flow:</p>
     * <ol>
     *   <li>Acquire pessimistic lock on account (SELECT FOR UPDATE)</li>
     *   <li>Check if account is already locked by another process</li>
     *   <li>Acquire distributed lock with transaction ID</li>
     *   <li>Validate and update balance</li>
     *   <li>JPA automatically increments version (optimistic lock)</li>
     *   <li>Release distributed lock</li>
     *   <li>Evict cache to ensure fresh reads</li>
     * </ol>
     *
     * <p>Retry Strategy:</p>
     * <ul>
     *   <li>Max Attempts: 3</li>
     *   <li>Initial Delay: 100ms</li>
     *   <li>Backoff Multiplier: 2.0 (100ms → 200ms → 400ms)</li>
     *   <li>Retry On: OptimisticLockingFailureException, OptimisticLockException</li>
     * </ul>
     *
     * @param accountNumber the account to update
     * @param amount the amount to add (can be negative for debits)
     * @param transactionId unique transaction identifier for lock tracking
     * @throws ResourceNotFoundException if account doesn't exist
     * @throws AccountLockedException if account is locked by another process
     * @throws IllegalArgumentException if resulting balance would be negative
     * @throws OptimisticLockingFailureException if version conflict occurs (triggers retry)
     */
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class, OptimisticLockException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @CacheEvict(value = {"accountVersions", "accountBalance"}, key = "#accountNumber")
    public void updateBalanceWithVersionCheck(
            String accountNumber,
            BigDecimal amount,
            String transactionId
    ) {
        String lockId = transactionId != null ? transactionId
                : UUID.randomUUID().toString();

        log.info("Starting balance update - Account: {}, Amount: {}, Transaction: {}",
                accountNumber, amount, lockId);

        try {
            // Step 1: Acquire pessimistic lock (SELECT FOR UPDATE)
            BankAccount account = bankAccountRepository.findByAccountNumberWithLock(accountNumber)
                    .orElseThrow(() -> {
                        log.error("Account not found: {}", accountNumber);
                        return new ResourceNotFoundException("BankAccount", "accountNumber", accountNumber);
                    });

            // Step 2: Check if account is already locked
            if (account.isLocked()) {
                log.warn("Account {} is already locked by {}", accountNumber
                        , account.getLockedBy());
                throw new AccountLockedException(accountNumber, account.getLockedBy());
            }

            // Step 3: Acquire distributed lock
            account.acquireLock(lockId);
            log.debug("Lock acquired on account {} by {}", accountNumber, lockId);

            // Step 4: Record current state
            Long oldVersion = account.getVersion();
            BigDecimal oldBalance = account.getBalance();
            BigDecimal newBalance = oldBalance.add(amount);

            // Step 5: Validate new balance
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.error("Insufficient balance - Account: {}, Current: {}, Requested: {}, Result: {}",
                        accountNumber, oldBalance, amount, newBalance);
                throw new IllegalArgumentException(
                        String.format("Insufficient balance. Current: %s, Requested: %s", oldBalance, amount)
                );
            }

            // Step 6: Update balance and timestamp
            account.setBalance(newBalance);
            account.setUpdatedAt(LocalDateTime.now());

            // Step 7: Save (JPA auto-increments version)
            BankAccount savedAccount = bankAccountRepository.save(account);

            // Step 8: Release lock
            savedAccount.releaseLock();
            bankAccountRepository.save(savedAccount);

            log.info("Balance updated successfully - Account: {}, Old Balance: {}, New Balance: {}, " +
                            "Old Version: {}, New Version: {}, Transaction: {}",
                    accountNumber, oldBalance, newBalance, oldVersion, savedAccount.getVersion(), lockId);

        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            log.warn("Optimistic lock failure for account {} (attempt will be retried)", accountNumber);
            throw e; // Let @Retryable handle retry
        } catch (Exception e) {
            log.error("Failed to update balance for account {}: {}", accountNumber, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Overload method without transaction ID.
     * Auto-generates a UUID for lock tracking.
     *
     * @param accountNumber the account to update
     * @param amount the amount to add (can be negative for debits)
     * @throws ResourceNotFoundException if account doesn't exist
     * @throws AccountLockedException if account is locked
     * @throws IllegalArgumentException if resulting balance would be negative
     */
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class
                    , OptimisticLockException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @CacheEvict(value = {"accountVersions", "accountBalance"}, key = "#accountNumber")
    public void updateBalanceWithVersionCheck(String accountNumber, BigDecimal amount) {
        updateBalanceWithVersionCheck(accountNumber, amount, null);
    }

    /**
     * Validates that an account exists and returns its current version.
     * Useful before starting a transaction to ensure account exists.
     *
     * @param accountNumber the account to validate
     * @return the current version number
     * @throws ResourceNotFoundException if account doesn't exist
     */
    @Cacheable(value = "accountVersions", key = "#accountNumber")
    public Long validateAndGetVersion(String accountNumber) {
        log.debug("Validating account and fetching version: {}", accountNumber);
        return getVersion(accountNumber);
    }

    /**
     * Manually evicts the cached version for an account.
     * Use when version changes occur outside this service.
     *
     * @param accountNumber the account whose cache to evict
     */
    @CacheEvict(value = {"accountVersions", "accountBalance"}, key = "#accountNumber")
    public void evictAccountVersionCache(String accountNumber) {
        log.debug("Evicting cache for account: {}", accountNumber);
    }

    /**
     * Checks if an account is currently locked.
     *
     * @param accountNumber the account to check
     * @return true if locked and lock hasn't expired, false otherwise
     */
    public boolean isAccountLocked(String accountNumber) {
        return bankAccountRepository.findByAccountNumber(accountNumber)
                .map(BankAccount::isLocked)
                .orElse(false);
    }

    /**
     * Forces release of a lock on an account.
     * Use with caution - only for administrative purposes or cleaning up stale locks.
     *
     * @param accountNumber the account to unlock
     * @throws ResourceNotFoundException if account doesn't exist
     */
    @Transactional
    @CacheEvict(value = {"accountVersions", "accountBalance"}, key = "#accountNumber")
    public void forceUnlock(String accountNumber) {
        log.warn("Force unlocking account: {}", accountNumber);

        BankAccount account = bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "accountNumber", accountNumber));

        account.releaseLock();
        bankAccountRepository.save(account);

        log.info("Account {} forcefully unlocked", accountNumber);
    }
}

