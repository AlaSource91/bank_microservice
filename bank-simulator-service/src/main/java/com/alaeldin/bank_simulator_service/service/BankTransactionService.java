package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.EventType;
import com.alaeldin.bank_simulator_service.constant.StatusTransaction;
import com.alaeldin.bank_simulator_service.constant.AccountStatus;
import com.alaeldin.bank_simulator_service.dto.TransferRequest;
import com.alaeldin.bank_simulator_service.exception.ResourceNotFoundException;
import com.alaeldin.bank_simulator_service.exception.AccountLockedException;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import com.alaeldin.bank_simulator_service.repository.BankTransactionRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service class for managing bank transaction operations.
 * This service handles all business logic related to fund transfers,
 * transaction processing, and transaction status tracking.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Process fund transfers between accounts via the Saga pattern</li>
 *   <li>Validate sufficient balance before transfer</li>
 *   <li>Create and manage transaction records</li>
 *   <li>Track transaction status (PENDING → PROCESSING → COMPLETED/FAILED)</li>
 *   <li>Cache transaction information for read performance</li>
 *   <li>Comprehensive logging for audit trail</li>
 * </ul>
 *
 * <p><b>Transaction flow:</b> Balance debit/credit, ledger entries and event
 * publishing are fully managed by {@link SagaOrchestrationService}.
 * This service is responsible only for lifecycle bookkeeping and delegation.</p>
 *
 * @see com.alaeldin.bank_simulator_service.model.BankTransaction
 * @see com.alaeldin.bank_simulator_service.repository.BankTransactionRepository
 * @see BankAccountService
 * @see SagaOrchestrationService
 */
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class BankTransactionService {

    private final BankAccountService bankAccountService;
    private final BankTransactionRepository bankTransactionRepository;
    private final EventPublishingService eventPublishingService;
    private final SagaOrchestrationService sagaOrchestrationService;

    /**
     * Constructor for dependency injection.
     * Uses constructor-based injection for better testability and immutability.
     *
     * @param bankAccountService        the service for account operations
     * @param bankTransactionRepository the repository for database operations
     * @param sagaOrchestrationService  the service for saga orchestration
     * @param eventPublishingService    the service for publishing events
     */
    public BankTransactionService(BankAccountService bankAccountService,
                                  BankTransactionRepository bankTransactionRepository,
                                  SagaOrchestrationService sagaOrchestrationService,
                                  EventPublishingService eventPublishingService) {
        this.bankAccountService = bankAccountService;
        this.bankTransactionRepository = bankTransactionRepository;
        this.eventPublishingService = eventPublishingService;
        this.sagaOrchestrationService = sagaOrchestrationService;
    }

    /**
     * Processes a fund transfer between two accounts.
     *
     * <p>This method is responsible for lifecycle bookkeeping only.  The actual
     * balance mutation (debit/credit), ledger entries, and event publishing are all
     * managed atomically by {@link SagaOrchestrationService#startSaga}.</p>
     *
     * <p>Transfer flow:</p>
     * <ol>
     *   <li>Validate the transfer request</li>
     *   <li>Create a {@code PROCESSING} transaction record</li>
     *   <li>Verify sufficient balance in the source account</li>
     *   <li>Delegate to the Saga orchestrator (debit → credit → ledger → events)</li>
     *   <li>Mark the transaction {@code COMPLETED} and persist</li>
     * </ol>
     *
     * @param transferRequest the transfer request containing source account, destination account, and amount
     * @return the transaction record with its final status ({@code COMPLETED} or {@code FAILED})
     * @throws IllegalArgumentException if the transfer request is null or invalid
     */
    @Caching(evict = {
        @CacheEvict(value = "accountBalance", key = "#transferRequest.sourceAccountNumber"),
        @CacheEvict(value = "accountBalance", key = "#transferRequest.destinationAccountNumber")
    })
    public BankTransaction processTransfer(TransferRequest transferRequest) {
        validateTransferRequest(transferRequest);

        log.info("Processing transfer: {} → {} amount={}",
                transferRequest.getSourceAccountNumber(),
                transferRequest.getDestinationAccountNumber(),
                transferRequest.getAmount());

        BankTransaction txn = createTransaction(transferRequest);
        txn.setStatus(StatusTransaction.PROCESSING);
        txn = bankTransactionRepository.save(txn);

        String transactionId = txn.getReferenceId();
        log.debug("Transaction record created: referenceId={}", transactionId);

        // Guard: verify sufficient balance before delegating to the Saga
        BigDecimal sourceBalance = bankAccountService.getBalance(transferRequest.getSourceAccountNumber());
        if (sourceBalance.compareTo(transferRequest.getAmount()) < 0) {
            log.warn("Insufficient funds – source={} required={} available={}",
                    transferRequest.getSourceAccountNumber(),
                    transferRequest.getAmount(),
                    sourceBalance);
            return failTransaction(txn, "Insufficient funds in source account");
        }

        try {
            // The Saga handles debit, credit, ledger entries, and event publishing atomically
            sagaOrchestrationService.startSaga(txn);

            txn.setStatus(StatusTransaction.COMPLETED);
            txn.setCompletedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());
            txn = bankTransactionRepository.save(txn);

            log.info("Transfer completed – referenceId={} amount={}", txn.getReferenceId(), txn.getAmount());
            return txn;

        } catch (AccountLockedException e) {
            log.warn("Transfer failed – account locked: referenceId={} error={}", transactionId, e.getMessage());
            txn = failTransaction(txn, "Account locked by another transaction: " + e.getMessage());
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);
            return txn;

        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            log.warn("Transfer failed – optimistic lock conflict: referenceId={} error={}", transactionId, e.getMessage());
            txn = failTransaction(txn, "Concurrent modification detected; transaction failed after retries");
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);
            return txn;

        } catch (IllegalArgumentException e) {
            log.warn("Transfer failed – validation error: referenceId={} error={}", transactionId, e.getMessage());
            txn = failTransaction(txn, "Validation error: " + e.getMessage());
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);
            return txn;

        } catch (Exception e) {
            log.error("Transfer failed – unexpected error: referenceId={} error={}", transactionId, e.getMessage(), e);
            txn = failTransaction(txn, "Transfer failed: " + e.getMessage());
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);
            return txn;
        }
    }

    /**
     * Creates a new transaction record from the transfer request.
     *
     * <p>Resolves account entities via {@link BankAccountService} and validates
     * that both accounts are {@code ACTIVE} before building the record.</p>
     *
     * @param transferRequest the transfer request containing account and amount details
     * @return newly created {@link BankTransaction} with {@code PENDING} status
     * @throws IllegalArgumentException  if either account is not active
     * @throws ResourceNotFoundException if either account number is not found
     */
    public BankTransaction createTransaction(TransferRequest transferRequest) {
        if (transferRequest == null) {
            throw new IllegalArgumentException("Transfer request cannot be null");
        }

        log.debug("Resolving accounts for new transaction");

        BankAccount sourceAccount = bankAccountService.findByAccountNumber(
                transferRequest.getSourceAccountNumber());
        BankAccount destinationAccount = bankAccountService.findByAccountNumber(
                transferRequest.getDestinationAccountNumber());

        validateAccountActive(sourceAccount, "Source");
        validateAccountActive(destinationAccount, "Destination");

        LocalDateTime now = LocalDateTime.now();

        BankTransaction txn = BankTransaction.builder()
                .referenceId("BANK_REF_" + UUID.randomUUID())
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .amount(transferRequest.getAmount())
                .description(transferRequest.getDescription())
                .status(StatusTransaction.PENDING)
                .transactionDate(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        log.debug("Transaction record built – referenceId={} amount={}",
                txn.getReferenceId(), txn.getAmount());

        return txn;
    }

    /**
     * Retrieves a transaction by its reference ID.
     * Results are cached under {@code "transactionStatus"} for performance.
     *
     * @param referenceId the unique transaction reference ID
     * @return the matching {@link BankTransaction}
     * @throws IllegalArgumentException  if {@code referenceId} is null or blank
     * @throws ResourceNotFoundException if no transaction exists with the given ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "transactionStatus", key = "#referenceId", unless = "#result == null")
    public BankTransaction getTransactionStatus(String referenceId) {
        validateNotBlank(referenceId, "Reference ID");

        log.info("Fetching transaction status for referenceId={}", referenceId);

        return bankTransactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found: referenceId={}", referenceId);
                    return new ResourceNotFoundException("BankTransaction", "referenceId", referenceId);
                });
    }

    /**
     * Retrieves transaction history for a specific account with pagination.
     * Returns all transactions where the account is either the source or destination.
     *
     * @param accountNumber the account number to get transaction history for
     * @param pageable      pagination and sorting parameters
     * @return paginated list of transactions for the account
     * @throws IllegalArgumentException  if {@code accountNumber} is null or blank
     * @throws ResourceNotFoundException if the account does not exist
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "transactionHistory", key = "#accountNumber + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<BankTransaction> getTransactionHistory(String accountNumber, Pageable pageable) {
        validateNotBlank(accountNumber, "Account number");
        validatePagination(pageable);

        log.info("Fetching transaction history for account={} page={} size={}",
                accountNumber, pageable.getPageNumber(), pageable.getPageSize());

        BankAccount account = bankAccountService.findByAccountNumber(accountNumber);
        Page<BankTransaction> page = bankTransactionRepository
                .findBySourceAccountOrDestinationAccount(account, account, pageable);

        log.debug("Found {} transactions for account={}", page.getTotalElements(), accountNumber);
        return page;
    }

    /**
     * Retrieves all transactions with pagination support.
     * Useful for admin operations and reporting.
     *
     * @param pageable pagination information
     * @return paginated list of all transactions
     * @throws IllegalArgumentException if pagination parameters are invalid
     */
    @Transactional(readOnly = true)
    public Page<BankTransaction> getAllTransactions(Pageable pageable) {
        validatePagination(pageable);

        log.info("Fetching all transactions - Page: {}, Size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<BankTransaction> transactions = bankTransactionRepository.findAll(pageable);

        log.debug("Found {} total transactions", transactions.getTotalElements());

        return transactions;
    }

    /**
     * Attempts to cancel a pending transaction.
     * Only transactions with {@code PENDING} status can be cancelled.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate reference ID</li>
     *   <li>Find transaction by reference ID</li>
     *   <li>Check if transaction can be cancelled ({@code PENDING} status only)</li>
     *   <li>Update status to {@code FAILED} and record the reason</li>
     *   <li>Evict transaction and history caches</li>
     * </ol>
     *
     * @param referenceId the transaction reference ID to cancel
     * @return the cancelled transaction
     * @throws IllegalArgumentException  if referenceId is null/empty
     * @throws ResourceNotFoundException if transaction not found
     * @throws IllegalStateException     if transaction is not in a cancellable state
     */
    @Caching(evict = {
        @CacheEvict(value = "transactionStatus", key = "#referenceId"),
        @CacheEvict(value = "transactionHistory", allEntries = true)
    })
    public BankTransaction cancelTransaction(String referenceId) {
        validateNotBlank(referenceId, "Reference ID");

        log.info("Attempting to cancel transaction: referenceId={}", referenceId);

        BankTransaction transaction = bankTransactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for cancellation: referenceId={}", referenceId);
                    return new ResourceNotFoundException("BankTransaction", "referenceId", referenceId);
                });

        if (transaction.getStatus() != StatusTransaction.PENDING) {
            log.warn("Cannot cancel transaction referenceId={} – current status is {}",
                    referenceId, transaction.getStatus());
            throw new IllegalStateException(
                    "Only PENDING transactions can be cancelled. Current status: " + transaction.getStatus());
        }

        transaction.setStatus(StatusTransaction.FAILED);
        transaction.setFailedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setErrorMessage("Transaction cancelled by user");

        BankTransaction saved = bankTransactionRepository.save(transaction);
        log.info("Transaction cancelled successfully: referenceId={}", referenceId);
        return saved;
    }

    /**
     * Retrieves a transaction by ID with accounts eagerly loaded.
     * This method ensures that the BankAccount entities are properly initialized
     * for response mapping, avoiding lazy loading issues.
     *
     * @param transactionId the transaction ID
     * @return {@link BankTransaction} with loaded accounts
     * @throws IllegalArgumentException  if {@code transactionId} is null
     * @throws ResourceNotFoundException if transaction not found
     */
    @Transactional(readOnly = true)
    public BankTransaction getTransactionWithAccounts(Long transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }

        log.debug("Fetching transaction with accounts for ID: {}", transactionId);

        BankTransaction transaction = bankTransactionRepository.findById(transactionId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found with ID: {}", transactionId);
                    return new ResourceNotFoundException("BankTransaction", "id", transactionId);
                });

        // Trigger lazy-loading of account associations while the session is still open.
        // The return values are intentionally discarded – we only need the side-effect
        // of initialising the Hibernate proxy.
        //noinspection ResultOfMethodCallIgnored
        if (transaction.getSourceAccount() != null) {
            transaction.getSourceAccount().getAccountNumber();
        }
        //noinspection ResultOfMethodCallIgnored
        if (transaction.getDestinationAccount() != null) {
            transaction.getDestinationAccount().getAccountNumber();
        }

        log.debug("Successfully fetched transaction with accounts - Reference: {}",
                transaction.getReferenceId());

        return transaction;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Marks a transaction as {@code FAILED}, persists it, and returns the saved entity.
     *
     * @param txn          the transaction to fail
     * @param errorMessage the human-readable failure reason
     * @return the persisted, failed {@link BankTransaction}
     */
    private BankTransaction failTransaction(BankTransaction txn, String errorMessage) {
        txn.setStatus(StatusTransaction.FAILED);
        txn.setErrorMessage(errorMessage);
        txn.setFailedAt(LocalDateTime.now());
        txn.setUpdatedAt(LocalDateTime.now());
        return bankTransactionRepository.save(txn);
    }

    /**
     * Asserts that a {@link BankAccount} has {@code ACTIVE} status.
     *
     * @param account the account to validate
     * @param label   a human-readable label used in error messages (e.g. "Source")
     * @throws IllegalArgumentException if the account is not {@code ACTIVE}
     */
    private void validateAccountActive(BankAccount account, String label) {
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            log.warn("{} account {} is not active – status={}",
                    label, account.getAccountNumber(), account.getAccountStatus());
            throw new IllegalArgumentException(
                    label + " account is not active. Status: " + account.getAccountStatus());
        }
    }

    /**
     * Asserts that a string value is neither {@code null} nor blank.
     *
     * @param value     the string to check
     * @param fieldName a human-readable field name used in error messages
     * @throws IllegalArgumentException if the value is null or blank
     */
    private void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
    }

    /**
     * Validates the transfer request to ensure all required fields are present and valid.
     *
     * @param transferRequest the transfer request to validate
     * @throws IllegalArgumentException if request is null or contains invalid data
     */
    private void validateTransferRequest(TransferRequest transferRequest) {
        if (transferRequest == null) {
            throw new IllegalArgumentException("Transfer request cannot be null");
        }

        validateNotBlank(transferRequest.getSourceAccountNumber(), "Source account number");
        validateNotBlank(transferRequest.getDestinationAccountNumber(), "Destination account number");

        if (transferRequest.getAmount() == null || transferRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        if (transferRequest.getSourceAccountNumber().equals(transferRequest.getDestinationAccountNumber())) {
            throw new IllegalArgumentException("Source and destination accounts cannot be the same");
        }
    }

    /**
     * Validates pagination parameters.
     *
     * @param pageable the pagination object to validate
     * @throws IllegalArgumentException if pagination parameters are invalid
     */
    private void validatePagination(Pageable pageable) {
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        if (pageable.getPageNumber() < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (pageable.getPageSize() <= 0 || pageable.getPageSize() > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
    }

    /**
     * Checks whether a transaction with the given reference ID exists.
     *
     * @param referenceId the transaction reference ID to check
     * @return {@code true} if the transaction exists, {@code false} otherwise
     * @throws IllegalArgumentException if referenceId is null or blank
     */
    @Transactional(readOnly = true)
    public boolean transactionExists(String referenceId) {
        validateNotBlank(referenceId, "Reference ID");
        boolean exists = bankTransactionRepository.findByReferenceId(referenceId).isPresent();
        log.debug("Transaction existence check for referenceId={}: {}", referenceId, exists);
        return exists;
    }
}
