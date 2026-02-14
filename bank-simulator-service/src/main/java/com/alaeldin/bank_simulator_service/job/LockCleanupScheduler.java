package com.alaeldin.bank_simulator_service.job;

import com.alaeldin.bank_simulator_service.model.BankAccount;
import com.alaeldin.bank_simulator_service.repository.BankAccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled service for cleaning up expired distributed locks on bank accounts.
 *
 * <p>Purpose:</p>
 * Prevents deadlocks by automatically releasing locks that have exceeded their
 * 5-minute timeout period. This handles cases where a transaction failed without
 * properly releasing its lock.
 *
 * <p>Schedule:</p>
 * Runs every 2 minutes to ensure timely cleanup of expired locks.
 *
 * @see BankAccount#isLockExpired()
 * @see BankAccount#releaseLock()
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LockCleanupScheduler {

    private final BankAccountRepository bankAccountRepository;
    private static final int LOCK_TIMEOUT_MINUTES = 5;

    /**
     * Cleans up expired locks on bank accounts.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Find all accounts with locks older than 5 minutes</li>
     *   <li>Release each expired lock</li>
     *   <li>Evict cache entries for affected accounts</li>
     *   <li>Log cleanup statistics</li>
     * </ol>
     *
     * <p>Schedule:</p>
     * Fixed delay of 2 minutes (120,000 ms) between executions.
     * Initial delay of 1 minute (60,000 ms) after application startup.
     */
    @Scheduled(fixedDelay = 120000, initialDelay = 60000) // Every 2 minutes, start after 1 minute
    @Transactional
    @CacheEvict(value = {"accountVersions", "accountBalance"}, allEntries = true)
    public void cleanupExpiredLocks() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(LOCK_TIMEOUT_MINUTES);

        log.debug("Starting lock cleanup - cutoff time: {}", cutoffTime);

        try {
            // Find all accounts with expired locks
            List<BankAccount> lockedAccounts = bankAccountRepository
                    .findLockedAccounts(cutoffTime);

            if (lockedAccounts.isEmpty()) {
                log.debug("No expired locks found");
                return;
            }

            log.info("Found {} accounts with expired locks", lockedAccounts.size());

            int cleanedCount = 0;
            for (BankAccount account : lockedAccounts) {
                if (account.isLockExpired()) {
                    String accountNumber = account.getAccountNumber();
                    String lockedBy = account.getLockedBy();
                    LocalDateTime lockTime = account.getLockTimestamp();

                    account.releaseLock();
                    bankAccountRepository.save(account);
                    cleanedCount++;

                    log.info("Released expired lock - Account: {}, Was locked by: {}, Lock time: {}",
                            accountNumber, lockedBy, lockTime);
                }
            }

            log.info("Lock cleanup completed - Released {} expired locks", cleanedCount);

        } catch (Exception e) {
            log.error("Error during lock cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for lock cleanup.
     * Can be called via admin endpoint for immediate cleanup.
     *
     * @return number of locks cleaned
     */
    @Transactional
    @CacheEvict(value = {"accountVersions", "accountBalance"}, allEntries = true)
    public int forceCleanup() {
        log.warn("Manual lock cleanup triggered");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(LOCK_TIMEOUT_MINUTES);
        List<BankAccount> lockedAccounts = bankAccountRepository.findLockedAccounts(cutoffTime);

        int cleanedCount = 0;
        for (BankAccount account : lockedAccounts) {
            if (account.isLockExpired()) {
                account.releaseLock();
                bankAccountRepository.save(account);
                cleanedCount++;
            }
        }

        log.info("Manual cleanup completed - Released {} locks", cleanedCount);
        return cleanedCount;
    }

    /**
     * Gets statistics about currently locked accounts.
     *
     * @return number of currently locked accounts
     */
    public long getLockedAccountCount() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(LOCK_TIMEOUT_MINUTES);
        return bankAccountRepository.findLockedAccounts(cutoffTime).size();
    }
}
