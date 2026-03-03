package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.EventType;
import com.alaeldin.bank_simulator_service.constant.StatusTransaction;
import com.alaeldin.bank_simulator_service.constant.AccountStatus;
import com.alaeldin.bank_simulator_service.dto.TransferRequest;
import com.alaeldin.bank_simulator_service.exception.ResourceNotFoundException;
import com.alaeldin.bank_simulator_service.exception.AccountLockedException;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import com.alaeldin.bank_simulator_service.repository.BankTransactionRepository;
import com.alaeldin.bank_simulator_service.repository.BankAccountRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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
 *   <li>Process fund transfers between accounts</li>
 *   <li>Validate sufficient balance before transfer</li>
 *   <li>Create and manage transaction records</li>
 *   <li>Track transaction status</li>
 *   <li>Cache transaction information for performance</li>
 *   <li>Comprehensive logging for audit trail</li>
 * </ul>
 *
 * @see com.alaeldin.bank_simulator_service.model.BankTransaction
 * @see com.alaeldin.bank_simulator_service.repository.BankTransactionRepository
 * @see BankAccountService
 */
@Service
@Slf4j
@Transactional
public class BankTransactionService {

    private final BankAccountService bankAccountService;
    private final AccountVersionService accountVersionService;
    private final BankTransactionRepository bankTransactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final LedgerService ledgerService;
    private final EventPublishingService eventPublishingService;

    /**
     * Constructor for dependency injection.
     * Uses constructor-based injection for better testability and immutability.
     *
     * @param bankAccountService the service for account operations
     * @param accountVersionService the service for distributed locking and version control
     * @param bankTransactionRepository the repository for database operations
     * @param bankAccountRepository the repository for bank account operations
     * @param ledgerService the service for ledger operations
     * @param eventPublishingService the service for publishing events
     */
    public BankTransactionService(BankAccountService bankAccountService,
                                   AccountVersionService accountVersionService,
                                   BankTransactionRepository bankTransactionRepository,
                                   BankAccountRepository bankAccountRepository,
                                   LedgerService ledgerService,
                                   EventPublishingService eventPublishingService) {
        this.bankAccountService = bankAccountService;
        this.accountVersionService = accountVersionService;
        this.bankTransactionRepository = bankTransactionRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.ledgerService = ledgerService;
        this.eventPublishingService = eventPublishingService;
    }

    /**
     * Processes a fund transfer between two accounts using distributed locking.
     * Uses AccountVersionService to ensure thread-safe operations with optimistic and pessimistic locking.
     * Validates the source account has sufficient balance, executes the transfer,
     * and creates a transaction record with appropriate status.
     *
     * <p>Process with Distributed Locking:</p>
     * <ol>
     *   <li>Validate transfer request</li>
     *   <li>Create transaction record to get reference ID for locking</li>
     *   <li>Check source account balance</li>
     *   <li>If insufficient funds: mark as FAILED and return</li>
     *   <li>Use AccountVersionService to debit source account with distributed lock</li>
     *   <li>Use AccountVersionService to credit destination account with distributed lock</li>
     *   <li>Mark transaction as COMPLETED</li>
     *   <li>Save transaction record</li>
     *   <li>Cache eviction handled by AccountVersionService</li>
     * </ol>
     *
     * <p>Distributed Locking Features:</p>
     * <ul>
     *   <li>Pessimistic locking (SELECT FOR UPDATE)</li>
     *   <li>Distributed lock with transaction ID</li>
     *   <li>Optimistic locking with automatic retry (3 attempts)</li>
     *   <li>Lock expiration (5 minutes)</li>
     *   <li>Automatic cache eviction</li>
     * </ul>
     *
     * @param transferRequest the transfer request containing source account, destination account, and amount
     * @return the transaction record with final status (COMPLETED or FAILED)
     * @throws IllegalArgumentException if transfer request is null or invalid
     * @throws RuntimeException if database operations fail
     */
    @CacheEvict(value = "accountBalance", allEntries = true)
    public BankTransaction processTransfer(TransferRequest transferRequest) {
        // Validate input
        validateTransferRequest(transferRequest);

        log.info("Processing transfer: {} -> {} Amount: {}",
                transferRequest.getSourceAccountNumber(),
                transferRequest.getDestinationAccountNumber(),
                transferRequest.getAmount());

        // Create transaction record early to get reference ID for distributed locking
        BankTransaction txn = createTransaction(transferRequest);
        txn.setStatus(StatusTransaction.PROCESSING);
        txn = bankTransactionRepository.save(txn);

        String transactionId = txn.getReferenceId();
        log.debug("Created transaction with ID: {} for distributed locking", transactionId);

        // Check if source account has sufficient balance
        BigDecimal fromAccountBalance = bankAccountService.getBalance(
                transferRequest.getSourceAccountNumber()
        );

        if (fromAccountBalance.compareTo(transferRequest.getAmount()) < 0) {
            log.warn("Transfer failed: Insufficient funds in source account {} - Required: {}, Available: {}",
                    transferRequest.getSourceAccountNumber(),
                    transferRequest.getAmount(),
                    fromAccountBalance);

            txn.setStatus(StatusTransaction.FAILED);
            txn.setErrorMessage("Insufficient funds in source account");
            txn.setFailedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());

            return bankTransactionRepository.save(txn);
        }

        // Execute the transfer using AccountVersionService with distributed locking
        try {
            log.debug("Debiting source account: {} Amount: {} Transaction: {}",
                    transferRequest.getSourceAccountNumber(),
                    transferRequest.getAmount(),
                    transactionId);

            // Use AccountVersionService for thread-safe balance update with distributed locking
            accountVersionService.updateBalanceWithVersionCheck(
                    transferRequest.getSourceAccountNumber(),
                    transferRequest.getAmount().negate(),
                    transactionId
            );

            log.debug("Crediting destination account: {} Amount: {} Transaction: {}",
                    transferRequest.getDestinationAccountNumber(),
                    transferRequest.getAmount(),
                    transactionId);

            // Use AccountVersionService for thread-safe balance update with distributed locking
            accountVersionService.updateBalanceWithVersionCheck(
                    transferRequest.getDestinationAccountNumber(),
                    transferRequest.getAmount(),
                    transactionId
            );

            txn.setStatus(StatusTransaction.COMPLETED);
            txn.setCompletedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());
            txn = bankTransactionRepository.save(txn);

            // Create ledger entries (this will also publish ledger events)
            ledgerService.createLedgerEntries(txn);

            // Publish transaction completed event
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_COMPLETED);

            log.info("Transfer completed successfully - Reference: {}, Amount: {}",
                    txn.getReferenceId(),
                    txn.getAmount());

            return txn;

        } catch (AccountLockedException e) {
            log.warn("Transfer failed due to account lock - Reference: {}, Error: {}",
                    transactionId, e.getMessage());

            txn.setStatus(StatusTransaction.FAILED);
            txn.setErrorMessage("Account locked by another transaction: " + e.getMessage());
            txn.setFailedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);
            return bankTransactionRepository.save(txn);

        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            log.warn("Transfer failed due to optimistic locking failure - Reference: {}, Error: {}",
                    transactionId, e.getMessage());

            txn.setStatus(StatusTransaction.FAILED);
            txn.setErrorMessage("Concurrent modification detected, transaction failed after retries");
            txn.setFailedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);

            return bankTransactionRepository.save(txn);

        } catch (IllegalArgumentException e) {
            log.warn("Transfer failed due to validation error - Reference: {}, Error: {}",
                    transactionId, e.getMessage());

            txn.setStatus(StatusTransaction.FAILED);
            txn.setErrorMessage("Validation error: " + e.getMessage());
            txn.setFailedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);
            return bankTransactionRepository.save(txn);

        } catch (Exception e) {
            log.error("Transfer failed with unexpected exception - Reference: {}, Error: {}",
                    transactionId, e.getMessage(), e);

            txn.setStatus(StatusTransaction.FAILED);
            txn.setErrorMessage("Transfer failed: " + e.getMessage());
            txn.setFailedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);
            return bankTransactionRepository.save(txn);
        }
    }

    /**
     * Creates a new transaction record from the transfer request.
     * Initializes transaction with pending status and current timestamps.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Create new BankTransaction entity</li>
     *   <li>Generate unique reference ID</li>
     *   <li>Set source and destination accounts</li>
     *   <li>Set amount and description</li>
     *   <li>Initialize timestamps</li>
     *   <li>Set status to PENDING</li>
     * </ol>
     *
     * @param transferRequest the transfer request containing account and amount details
     * @return newly created BankTransaction with initial values
     * @throws IllegalArgumentException if transfer request is null
     */
    public BankTransaction createTransaction(TransferRequest transferRequest) {
        if (transferRequest == null) {
            log.error("Failed to create transaction: transferRequest is null");
            throw new IllegalArgumentException("Transfer request cannot be null");
        }

        log.debug("Creating transaction record from transfer request");

        // Fetch the actual BankAccount objects using the account numbers
        var sourceAccount = bankAccountRepository.findByAccountNumber(transferRequest.getSourceAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "accountNumber", transferRequest.getSourceAccountNumber()));
        var destinationAccount = bankAccountRepository.findByAccountNumber(transferRequest.getDestinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", "accountNumber", transferRequest.getDestinationAccountNumber()));

        // Validate account status - both accounts must be ACTIVE
        if (sourceAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            log.error("Source account {} is not active. Status: {}", sourceAccount.getAccountNumber(), sourceAccount.getAccountStatus());
            throw new IllegalArgumentException("Source account is not active. Account status: " + sourceAccount.getAccountStatus());
        }

        if (destinationAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            log.error("Destination account {} is not active. Status: {}", destinationAccount.getAccountNumber(), destinationAccount.getAccountStatus());
            throw new IllegalArgumentException("Destination account is not active. Account status: " + destinationAccount.getAccountStatus());
        }

        BankTransaction txn = new BankTransaction();
        txn.setReferenceId("BANK_REF_" + UUID.randomUUID());
        txn.setSourceAccount(sourceAccount);
        txn.setDestinationAccount(destinationAccount);
        txn.setAmount(transferRequest.getAmount());
        txn.setDescription(transferRequest.getDescription());
        txn.setStatus(StatusTransaction.PENDING);

        LocalDateTime now = LocalDateTime.now();
        txn.setTransactionDate(now);
        txn.setCreatedAt(now);
        txn.setUpdatedAt(now);

        log.debug("Transaction created - Reference: {}, Amount: {}",
                txn.getReferenceId(),
                txn.getAmount());

        return txn;
    }

    /**
     * Retrieves the status of a transaction by its reference ID.
     * Results are cached for performance optimization.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate reference ID is not null or empty</li>
     *   <li>Query database for transaction</li>
     *   <li>Return transaction if found</li>
     *   <li>Throw exception if not found</li>
     * </ol>
     *
     * <p>Note: Results are cached in "transactionStatus" cache for 5 minutes.</p>
     *
     * @param referenceId the unique transaction reference ID
     * @return the transaction record with complete details and status
     * @throws IllegalArgumentException if referenceId is null or empty
     * @throws RuntimeException if transaction is not found
     */
    @Cacheable(value = "transactionStatus", key = "#referenceId", unless = "#result == null")
    public BankTransaction getTransactionStatus(String referenceId) {
        // Validate input
        validateReferenceId(referenceId);

        log.info("Fetching transaction status for referenceId: {}", referenceId);

        BankTransaction transaction = bankTransactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found with referenceId: {}", referenceId);
                    return new RuntimeException("Transaction not found with referenceId: " + referenceId);
                });

        log.debug("Transaction found - Reference: {}, Status: {}", referenceId, transaction.getStatus());

        return transaction;
    }

    /**
     * Retrieves transaction history for a specific account with pagination.
     * Returns all transactions where the account is either source or destination.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate account number and pagination parameters</li>
     *   <li>Query database for account transactions</li>
     *   <li>Return paginated results</li>
     * </ol>
     *
     * @param accountNumber the account number to get transaction history for
     * @param pageable pagination information
     * @return paginated list of transactions for the account
     * @throws IllegalArgumentException if accountNumber is null/empty or pagination is invalid
     */
    @Cacheable(value = "transactionHistory", key = "#accountNumber + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<BankTransaction> getTransactionHistory(String accountNumber, Pageable pageable) {
        validateReferenceId(accountNumber); // Reuse validation method
        validatePagination(pageable);

        log.info("Fetching transaction history for account: {} - Page: {}, Size: {}",
                accountNumber, pageable.getPageNumber(), pageable.getPageSize());

        // TODO: Implement custom repository method findBySourceAccountOrDestinationAccount
        // For now, return all transactions (should be filtered in repository)
        Page<BankTransaction> transactions = bankTransactionRepository.findAll(pageable);

        log.debug("Found {} transactions for account: {}", transactions.getTotalElements(), accountNumber);

        return transactions;
    }

    /**
     * Retrieves all transactions with pagination support.
     * Useful for admin operations and reporting.
     *
     * @param pageable pagination information
     * @return paginated list of all transactions
     * @throws IllegalArgumentException if pagination parameters are invalid
     */
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
     * Only transactions with PENDING status can be cancelled.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate reference ID</li>
     *   <li>Find transaction by reference ID</li>
     *   <li>Check if transaction can be cancelled (PENDING status)</li>
     *   <li>Update status to CANCELLED</li>
     *   <li>Evict transaction from cache</li>
     * </ol>
     *
     * @param referenceId the transaction reference ID to cancel
     * @return the cancelled transaction
     * @throws IllegalArgumentException if referenceId is null/empty
     * @throws ResourceNotFoundException if transaction not found
     * @throws IllegalStateException if transaction cannot be cancelled
     */
    @CacheEvict(value = "transactionStatus", key = "#referenceId")
    public BankTransaction cancelTransaction(String referenceId) {
        validateReferenceId(referenceId);

        log.info("Attempting to cancel transaction: {}", referenceId);

        BankTransaction transaction = bankTransactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for cancellation: {}", referenceId);
                    return new ResourceNotFoundException("BankTransaction", "referenceId", referenceId);
                });

        if (transaction.getStatus() != StatusTransaction.PENDING) {
            log.warn("Cannot cancel transaction {} with status: {}",
                    referenceId, transaction.getStatus());
            throw new IllegalStateException("Transaction cannot be cancelled. Current status: " +
                    transaction.getStatus());
        }

        transaction.setStatus(StatusTransaction.FAILED);
        transaction.setFailedAt(LocalDateTime.now());
        transaction.setErrorMessage("Transaction cancelled by user");

        BankTransaction savedTransaction = bankTransactionRepository.save(transaction);

        log.info("Transaction cancelled successfully: {}", referenceId);

        return savedTransaction;
    }

    /**
     * Retrieves a transaction by ID with accounts eagerly loaded.
     * This method ensures that the BankAccount entities are properly initialized
     * for response mapping, avoiding lazy loading issues.
     *
     * @param transactionId the transaction ID
     * @return BankTransaction with loaded accounts
     * @throws ResourceNotFoundException if transaction not found
     */
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

        // Trigger lazy loading of accounts within active transaction context
        if (transaction.getSourceAccount() != null) {
            transaction.getSourceAccount().getAccountNumber(); // Force initialization
        }
        if (transaction.getDestinationAccount() != null) {
            transaction.getDestinationAccount().getAccountNumber(); // Force initialization
        }

        log.debug("Successfully fetched transaction with accounts - Reference: {}",
                transaction.getReferenceId());

        return transaction;
    }

    /**
     * Validates the transfer request to ensure all required fields are present and valid.
     *
     * @param transferRequest the transfer request to validate
     * @throws IllegalArgumentException if request is null or contains invalid data
     */
    private void validateTransferRequest(TransferRequest transferRequest) {
        if (transferRequest == null) {
            log.error("Failed to validate transfer request: transferRequest is null");
            throw new IllegalArgumentException("Transfer request cannot be null");
        }

        if (transferRequest.getSourceAccountNumber() == null || transferRequest.getSourceAccountNumber().isBlank()) {
            log.error("Failed to validate transfer request: source account number is null or blank");
            throw new IllegalArgumentException("Source account number cannot be null or blank");
        }

        if (transferRequest.getDestinationAccountNumber() == null || transferRequest.getDestinationAccountNumber().isBlank()) {
            log.error("Failed to validate transfer request: destination account number is null or blank");
            throw new IllegalArgumentException("Destination account number cannot be null or blank");
        }

        if (transferRequest.getAmount() == null || transferRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Failed to validate transfer request: invalid amount {}", transferRequest.getAmount());
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        // Check if source and destination are the same
        if (transferRequest.getSourceAccountNumber().equals(transferRequest.getDestinationAccountNumber())) {
            log.warn("Transfer between same account detected");
            throw new IllegalArgumentException("Source and destination accounts cannot be the same");
        }
    }

    /**
     * Validates the reference ID to ensure it is not null or empty.
     *
     * @param referenceId the reference ID to validate
     * @throws IllegalArgumentException if referenceId is null or empty
     */
    private void validateReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            log.error("Failed to validate reference ID: referenceId is null or blank");
            throw new IllegalArgumentException("Reference ID cannot be null or empty");
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
            log.error("Failed to validate pagination: pageable is null");
            throw new IllegalArgumentException("Pageable cannot be null");
        }

        if (pageable.getPageNumber() < 0) {
            log.error("Failed to validate pagination: page number {} is negative",
                    pageable.getPageNumber());
            throw new IllegalArgumentException("Page number cannot be negative");
        }

        if (pageable.getPageSize() <= 0 || pageable.getPageSize() > 100) {
            log.error("Failed to validate pagination: invalid page size {}",
                    pageable.getPageSize());
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
    }

    /**
     * Checks if a transaction with the given reference ID exists.
     *
     * @param referenceId the transaction reference ID to check
     * @return true if transaction exists, false otherwise
     */
    public boolean transactionExists(String referenceId) {
        validateReferenceId(referenceId);

        boolean exists = bankTransactionRepository.findByReferenceId(referenceId).isPresent();

        log.debug("Transaction existence check for {}: {}", referenceId, exists);

        return exists;
    }
}
