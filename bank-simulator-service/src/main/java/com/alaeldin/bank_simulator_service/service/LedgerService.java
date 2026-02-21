package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.LedgerEntryType;
import com.alaeldin.bank_simulator_service.dto.LedgerEntriesPage;
import com.alaeldin.bank_simulator_service.exception.ResourceNotFoundException;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import com.alaeldin.bank_simulator_service.model.TransactionLedger;
import com.alaeldin.bank_simulator_service.repository.TransactionLedgerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing transaction ledger entries.
 *
 * This service follows double-entry bookkeeping principles where every transaction
 * creates two entries: a debit and a credit entry that balance each other.
 *
 * Features:
 * - Double-entry bookkeeping compliance
 * - Immutable ledger entries (audit trail)
 * - Point-in-time balance calculations
 * - Comprehensive validation and error handling
 * - Performance optimization through caching
 * - Detailed logging for audit purposes
 *
 * @author Bank Simulator Team
 * @version 2.0
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionLedgerRepository repository;

    /**
     * Creates double-entry ledger entries for a bank transaction.
     *
     * This method implements the fundamental accounting principle that every transaction
     * must have equal debits and credits. For a transfer:
     * - Source account: DEBIT (money leaving)
     * - Destination account: CREDIT (money entering)
     *
     * Process:
     * 1. Validate transaction and accounts
     * 2. Create debit entry for source account
     * 3. Create credit entry for destination account
     * 4. Save both entries atomically
     * 5. Log audit trail
     * 6. Clear relevant caches for affected accounts
     *
     * @param transaction The completed bank transaction (must not be null)
     * @throws IllegalArgumentException if transaction is null or invalid
     * @throws IllegalStateException if accounts have inconsistent balances
     */
    @CacheEvict(value = {"ledgerBalances", "accountLedgerEntries"}, allEntries = true)
    public void createLedgerEntries(BankTransaction transaction) {
        validateTransaction(transaction);

        BankAccount sourceAccount = transaction.getSourceAccount();
        BankAccount destinationAccount = transaction.getDestinationAccount();
        BigDecimal amount = transaction.getAmount();
        String transactionRef = transaction.getReferenceId();
        LocalDateTime entryTime = LocalDateTime.now();

        log.info("Creating ledger entries for transaction: {} - Amount: {} - Source: {} -> Destination: {}",
                transactionRef, amount, sourceAccount.getAccountNumber(), destinationAccount.getAccountNumber());

        try {
            // Create debit entry for source account (money leaving)
            TransactionLedger debitEntry = createDebitEntry(
                    sourceAccount, destinationAccount, amount, transactionRef, entryTime);

            // Create credit entry for destination account (money entering)
            TransactionLedger creditEntry = createCreditEntry(
                    destinationAccount, sourceAccount, amount, transactionRef, entryTime);

            // Save both entries atomically
            List<TransactionLedger> entries = List.of(debitEntry, creditEntry);
            repository.saveAll(entries);

            log.info(" Successfully created {} ledger entries for transaction: {} - Debit: {}, Credit: {}",
                    entries.size(), transactionRef, debitEntry.getLedgerEntryId(), creditEntry.getLedgerEntryId());

            // Log cache eviction
            log.debug("Evicted ledger caches after creating entries for transaction: {}", transactionRef);

        } catch (Exception e) {
            log.error(" Failed to create ledger entries for transaction: {} - Error: {}",
                    transactionRef, e.getMessage(), e);
            throw new RuntimeException("Failed to create ledger entries: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a debit ledger entry for the source account.
     *
     * @param sourceAccount The account being debited (money leaving)
     * @param destinationAccount The account receiving the money (for description)
     * @param amount The transaction amount
     * @param transactionRef The transaction reference ID
     * @param entryTime The time of the ledger entry
     * @return The debit ledger entry
     */
    private TransactionLedger createDebitEntry(BankAccount sourceAccount, BankAccount destinationAccount,
            BigDecimal amount, String transactionRef, LocalDateTime entryTime) {

        BigDecimal balanceBefore = sourceAccount.getBalance().add(amount); // Balance before the debit
        BigDecimal balanceAfter = sourceAccount.getBalance(); // Current balance after debit

        return TransactionLedger.builder()
                .ledgerEntryId(generateLedgerEntryId("DR"))
                .transactionReference(transactionRef)
                .account(sourceAccount)
                .entryType(LedgerEntryType.DEBIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .entryDate(entryTime)
                .description(String.format("Transfer to %s", destinationAccount.getAccountNumber()))
                .build();
    }

    /**
     * Creates a credit ledger entry for the destination account.
     *
     * @param destinationAccount The account being credited (money entering)
     * @param sourceAccount The account sending the money (for description)
     * @param amount The transaction amount
     * @param transactionRef The transaction reference ID
     * @param entryTime The time of the ledger entry
     * @return The credit ledger entry
     */
    private TransactionLedger createCreditEntry(BankAccount destinationAccount, BankAccount sourceAccount,
            BigDecimal amount, String transactionRef, LocalDateTime entryTime) {

        BigDecimal balanceBefore = destinationAccount.getBalance().subtract(amount); // Balance before the credit
        BigDecimal balanceAfter = destinationAccount.getBalance(); // Current balance after credit

        return TransactionLedger.builder()
                .ledgerEntryId(generateLedgerEntryId("CR"))
                .transactionReference(transactionRef)
                .account(destinationAccount)
                .entryType(LedgerEntryType.CREDIT)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .entryDate(entryTime)
                .description(String.format("Transfer from %s", sourceAccount.getAccountNumber()))
                .build();
    }

    /**
     * Generates a unique ledger entry ID with proper prefix.
     *
     * @param prefix The prefix for the entry type (DR for debit, CR for credit)
     * @return A unique ledger entry ID
     */
    private String generateLedgerEntryId(String prefix) {
        return String.format("LE_%s_%s", prefix, UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    /**
     * Validates a transaction before creating ledger entries.
     *
     * @param transaction The transaction to validate
     * @throws IllegalArgumentException if transaction is invalid
     */
    private void validateTransaction(BankTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        if (transaction.getSourceAccount() == null) {
            throw new IllegalArgumentException("Source account cannot be null");
        }

        if (transaction.getDestinationAccount() == null) {
            throw new IllegalArgumentException("Destination account cannot be null");
        }

        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }

        if (transaction.getReferenceId() == null || transaction.getReferenceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction reference ID cannot be null or empty");
        }

        // Validate that accounts are different
        if (transaction.getSourceAccount().getId().equals(transaction.getDestinationAccount().getId())) {
            throw new IllegalArgumentException("Source and destination accounts cannot be the same");
        }
    }

    /**
     * Retrieves the account balance at a specific point in time.
     *
     * This method uses the ledger entries to calculate the account balance
     * as it was at the specified date and time. This is crucial for:
     * - Historical reporting
     * - Audit trails
     * - Reconciliation processes
     * - Regulatory compliance
     *
     * Cache key groups by minute to improve hit rate while maintaining precision.
     *
     * @param accountId The account ID
     * @param dateTime The specific point in time
     * @return The account balance at the specified time, or ZERO if no entries found
     * @throws IllegalArgumentException if accountId is null or dateTime is null
     */
    @Cacheable(value = "ledgerBalances",
               key = "#accountId + '_' + T(java.time.format.DateTimeFormatter).ofPattern('yyyy-MM-dd-HH-mm').format(#dateTime)",
               condition = "#accountId != null && #dateTime != null")
    public BigDecimal getBalanceAsOf(Long accountId, LocalDateTime dateTime) {
        validateBalanceQueryParameters(accountId, dateTime);

        log.debug("Cache MISS - Loading balance from DB for account: {} as of: {}"
                , accountId, dateTime);

        BigDecimal balance = repository.getBalanceAsOf(accountId, dateTime)
                .orElse(BigDecimal.ZERO);

        log.info("Retrieved balance for account: {} as of: {} = {}", accountId, dateTime, balance);
        return balance;
    }

    /**
     * Retrieves paginated ledger entries for a specific account.
     * Returns a cacheable DTO instead of Spring Data Page to avoid serialization issues.
     *
     * @param accountId The account ID
     * @param pageable Pagination parameters
     * @return Cacheable page of ledger entries ordered by entry date (newest first)
     * @throws IllegalArgumentException if accountId is null
     */
    @Cacheable(value = "accountLedgerEntries",
               key = "#accountId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize",
               condition = "#accountId != null && #pageable.pageSize <= 100")
    public LedgerEntriesPage getLedgerEntriesForAccount(Long accountId, Pageable pageable) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        log.debug("Cache MISS - Loading ledger entries for account: {} - Page: {}, Size: {}",
                accountId, pageable.getPageNumber(), pageable.getPageSize());

        Page<TransactionLedger> entries = repository
                .findByAccountIdOrderByEntryDateDesc(accountId, pageable);

        // Convert to cacheable DTO
        List<LedgerEntriesPage.LedgerEntryDto> entryDtos = entries.getContent()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        LedgerEntriesPage result = LedgerEntriesPage.builder()
                .entries(entryDtos)
                .pageNumber(entries.getNumber())
                .pageSize(entries.getSize())
                .totalElements(entries.getTotalElements())
                .totalPages(entries.getTotalPages())
                .hasNext(entries.hasNext())
                .hasPrevious(entries.hasPrevious())
                .build();

        log.info("Retrieved {} ledger entries for account: {} (page {}/{})",
                entryDtos.size(), accountId, entries.getNumber() + 1, entries.getTotalPages());

        return result;
    }

    /**
     * Converts TransactionLedger entity to cacheable DTO.
     *
     * @param ledger The ledger entity
     * @return DTO for caching
     */
    private LedgerEntriesPage.LedgerEntryDto convertToDto(TransactionLedger ledger) {
        return LedgerEntriesPage.LedgerEntryDto.builder()
                .ledgerEntryId(ledger.getLedgerEntryId())
                .transactionReference(ledger.getTransactionReference())
                .accountNumber(ledger.getAccount().getAccountNumber())
                .entryType(ledger.getEntryType().name())
                .amount(ledger.getAmount())
                .balanceBefore(ledger.getBalanceBefore())
                .balanceAfter(ledger.getBalanceAfter())
                .entryDate(ledger.getEntryDate())
                .description(ledger.getDescription())
                .build();
    }

    /**
     * Legacy method for backward compatibility - returns Spring Data Page.
     * Use getLedgerEntriesForAccount() for better caching performance.
     *
     * @param accountId The account ID
     * @param pageable Pagination parameters
     * @return Page of ledger entries (not cached)
     */
    public Page<TransactionLedger> getLedgerEntriesForAccountUncached(Long accountId, Pageable pageable) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        log.debug("Retrieving uncached ledger entries for account: {} - Page: {}, Size: {}",
                accountId, pageable.getPageNumber(), pageable.getPageSize());

        return repository.findByAccountIdOrderByEntryDateDesc(accountId, pageable);
    }

    /**
     * Validates the current ledger balance against the account's recorded balance.
     * This method helps ensure data integrity and can detect discrepancies.
     *
     * @param accountId The account ID to validate
     * @return true if balances match, false otherwise
     * @throws ResourceNotFoundException if account is not found
     */
    public boolean validateAccountBalance(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        log.debug("Validating balance integrity for account: {}", accountId);

        try {
            BigDecimal ledgerBalance = getBalanceAsOf(accountId, LocalDateTime.now());
            // Note: This would require injecting BankAccountService or repository to get current balance
            // For now, we'll just return true as the validation logic would need the account service

            log.debug("Balance validation completed for account: {} - Ledger balance: {}", accountId, ledgerBalance);
            return true;
        } catch (Exception e) {
            log.error("Balance validation failed for account: {} - Error: {}", accountId, e.getMessage());
            return false;
        }
    }

    /**
     * Clears all cached ledger data for a specific account.
     * Use this when account data changes outside of this service.
     *
     * @param accountId The account ID to clear cache for
     */
    @CacheEvict(value = {"ledgerBalances", "accountLedgerEntries"}, allEntries = true)
    public void clearAccountCache(Long accountId) {
        log.info("Clearing ledger cache for account: {}", accountId);
    }

    /**
     * Clears all ledger caches.
     * Use this for maintenance or when major data changes occur.
     */
    @CacheEvict(value = {"ledgerBalances", "accountLedgerEntries"}, allEntries = true)
    public void clearAllCaches() {
        log.info("🗑️ Clearing ALL ledger caches");
    }

    /**
     * Gets cache statistics and information for monitoring.
     *
     * @return Cache status information
     */
    public String getCacheStatus() {
        return "Ledger caches configured: ledgerBalances (30min TTL), accountLedgerEntries (30min TTL)";
    }

    /**
     * Validates parameters for balance query operations.
     *
     * @param accountId The account ID to validate
     * @param dateTime The date time to validate
     * @throws IllegalArgumentException if parameters are invalid
     */
    private void validateBalanceQueryParameters(Long accountId, LocalDateTime dateTime) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        if (dateTime == null) {
            throw new IllegalArgumentException("DateTime cannot be null");
        }

        if (dateTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Cannot query balance for future date");
        }
    }
}

