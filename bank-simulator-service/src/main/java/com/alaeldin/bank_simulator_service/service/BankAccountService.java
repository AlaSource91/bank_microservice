package com.alaeldin.bank_simulator_service.service;



import com.alaeldin.bank_simulator_service.util.AccountNumberUtil;
import com.alaeldin.bank_simulator_service.constant.AccountStatus;
import com.alaeldin.bank_simulator_service.constant.AccountType;
import com.alaeldin.bank_simulator_service.dto.BankAccountRequest;
import com.alaeldin.bank_simulator_service.dto.BankAccountResponse;
import com.alaeldin.bank_simulator_service.exception.AccountHolderNameAlreadyExist;
import com.alaeldin.bank_simulator_service.exception.ResourceNotFoundException;
import com.alaeldin.bank_simulator_service.mapper.BankAccountMapper;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import com.alaeldin.bank_simulator_service.repository.BankAccountRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service class for managing bank account operations.
 * This service handles all business logic related to bank accounts including creation,
 * retrieval, update, and status management.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Create new bank accounts with validation</li>
 *   <li>Retrieve accounts by account number or status</li>
 *   <li>Update account information</li>
 *   <li>Freeze/unfreeze accounts</li>
 *   <li>Close accounts</li>
 *   <li>Comprehensive logging for audit trail</li>
 * </ul>
 *
 * @see com.alaeldin.bank_simulator_service.model.BankAccount
 * @see com.alaeldin.bank_simulator_service.repository.BankAccountRepository
 */
@Slf4j
@Service
@Transactional
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final BankAccountMapper bankAccountMapper;

    /**
     * Constructor for dependency injection.
     * Uses constructor-based injection for better testability and immutability.
     *
     * @param bankAccountRepository the repository for database operations
     * @param bankAccountMapper     the mapper for DTO conversions
     */
    public BankAccountService(BankAccountRepository bankAccountRepository, BankAccountMapper bankAccountMapper) {
        this.bankAccountRepository = bankAccountRepository;
        this.bankAccountMapper = bankAccountMapper;
    }

    /**
     * Creates a new bank account with the provided information.
     * Validates that the account holder name is unique and initializes the account with default values.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate input request is not null</li>
     *   <li>Check if account holder name already exists</li>
     *   <li>Generate unique account number</li>
     *   <li>Map request DTO to entity</li>
     *   <li>Set default values (status, timestamps)</li>
     *   <li>Save account to database</li>
     *   <li>Return response DTO</li>
     * </ol>
     *
     * @param bankAccountRequest the request DTO containing account creation details
     * @return a BankAccountResponse containing the created account information
     * @throws IllegalArgumentException if bankAccountRequest is null
     * @throws AccountHolderNameAlreadyExist if an account with the same holder name already exists
     */
    public BankAccountResponse createBankAccount(BankAccountRequest bankAccountRequest) {
        // Validate input
        if (bankAccountRequest == null) {
            log.error("Failed to create bank account: bankAccountRequest is null");
            throw new IllegalArgumentException("Bank account request cannot be null");
        }

        String accountHolderName = bankAccountRequest.getAccountHolderName();

        log.info("Creating bank account for holder: {} with type: {}",
                accountHolderName,
                bankAccountRequest.getAccountType().getDisplayName());

        // Check if an account with the same holder name already exists
        if (bankAccountRepository.existsByAccountHolderName(accountHolderName)) {
            log.warn("Account holder name '{}' already exists. Cannot create duplicate account.", accountHolderName);
            throw new AccountHolderNameAlreadyExist("accountHolderName", accountHolderName);
        }

        // Generate unique account number
        String accountNumber = AccountNumberUtil.generateAccountNumber();
        log.debug("Generated unique account number: {}", accountNumber);

        // Map request DTO to entity
        BankAccount bankAccount = bankAccountMapper.toEntity(bankAccountRequest);

        // Set default values for new account
        bankAccount.setAccountNumber(accountNumber);
        bankAccount.setAccountStatus(AccountStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        bankAccount.setCreatedAt(now);
        bankAccount.setUpdatedAt(now);

        // Save account to database
        BankAccount savedAccount = bankAccountRepository.save(bankAccount);
        log.debug("Account persisted to database with ID: {}", savedAccount.getId());

        // Map entity to response DTO
        BankAccountResponse bankAccountResponse = bankAccountMapper.toDto(savedAccount);

        log.info("Bank account created successfully - Account Number: {}, Holder: {}, ID: {}",
                savedAccount.getAccountNumber(),
                savedAccount.getAccountHolderName(),
                savedAccount.getId());

        return bankAccountResponse;
    }

    /**
     * Retrieves all bank accounts with pagination support.
     * This method retrieves a paginated list of all bank accounts from the database,
     * sorted and filtered according to the provided pagination parameters.
     *
     * <p>Pagination Details:</p>
     * <ul>
     *   <li>Page numbering starts at 0 (first page is page 0)</li>
     *   <li>Size must be greater than 0 (page size)</li>
     *   <li>Returns empty page if no accounts exist</li>
     * </ul>
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate pagination parameters (page >= 0 and size > 0)</li>
     *   <li>Create Pageable object with specified page and size</li>
     *   <li>Query database for paginated accounts</li>
     *   <li>Map entities to response DTOs</li>
     *   <li>Return paginated response</li>
     * </ol>
     *
     * @param page the page number (0-indexed, must be >= 0)
     * @param size the number of records per page (must be > 0)
     * @return a Page of BankAccountResponse objects containing the requested page of accounts
     * @throws IllegalArgumentException if page is negative or size is less than or equal to 0
     *
     * @see org.springframework.data.domain.Page
     * @see org.springframework.data.domain.Pageable
     */
    public Page<BankAccountResponse> getAllAccounts(int page, int size) {
        // Validate pagination parameters
        if (page < 0) {
            log.error("Failed to retrieve accounts: page number {} is negative", page);
            throw new IllegalArgumentException("Page number cannot be negative. Provided: " + page);
        }

        if (size <= 0) {
            log.error("Failed to retrieve accounts: page size {} is invalid", size);
            throw new IllegalArgumentException("Page size must be greater than 0. Provided: " + size);
        }

        log.info("Retrieving bank accounts - Page: {}, Size: {} (records per page)", page, size);

        // Create Pageable object with specified page and size
        Pageable pageable = Pageable.ofSize(size).withPage(page);

        // Query database for paginated accounts
        Page<BankAccount> bankAccounts = bankAccountRepository.findAll(pageable);

        log.debug("Retrieved {} accounts from page {}. Total pages: {}, Total records: {}",
                bankAccounts.getNumberOfElements(),
                page,
                bankAccounts.getTotalPages(),
                bankAccounts.getTotalElements());

        // Map entities to response DTOs and return
        Page<BankAccountResponse> response = bankAccounts.map(bankAccountMapper::toDto);

        log.info("Successfully retrieved paginated accounts - Page: {}, Records returned: {}/{}",
                page,
                response.getNumberOfElements(),
                size);

        return response;
    }

    /**
     * Retrieves a bank account by its unique identifier (ID).
     * This method queries the database for an account with the specified ID and returns
     * the account information mapped to a response DTO.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate input ID is not null and is positive</li>
     *   <li>Query database for account with specified ID</li>
     *   <li>Throw ResourceNotFoundException if not found</li>
     *   <li>Map entity to response DTO</li>
     *   <li>Return response DTO</li>
     * </ol>
     *
     * @param id the unique account identifier (must be > 0)
     * @return a BankAccountResponse containing the account information
     * @throws IllegalArgumentException if id is null or less than or equal to 0
     * @throws ResourceNotFoundException if no account is found with the specified ID
     */
    public BankAccountResponse getAccountById(Long id) {
        // Validate input
        if (id == null) {
            log.error("Failed to retrieve account: id is null");
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        if (id <= 0) {
            log.error("Failed to retrieve account: id {} is not positive", id);
            throw new IllegalArgumentException("Account ID must be greater than 0. Provided: " + id);
        }

        log.info("Retrieving account with ID: {}", id);

        // Query database for account with specified ID
        BankAccount bankAccount = bankAccountRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Account not found with ID: {}", id);
                    return new ResourceNotFoundException("BankAccount", "id", id);
                });

        log.debug("Account found with ID: {} - Account Number: {}", id, bankAccount.getAccountNumber());

        // Map entity to response DTO
        BankAccountResponse response = bankAccountMapper.toDto(bankAccount);

        log.info("Successfully retrieved account - ID: {}, Account Number: {}, Holder: {}",
                id,
                bankAccount.getAccountNumber(),
                bankAccount.getAccountHolderName());

        return response;
    }

    /**
     * Retrieves a bank account by its unique account number.
     * This method queries the database for an account with the specified account number
     * and returns the account information mapped to a response DTO.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate input account number is not null or empty</li>
     *   <li>Query database for account with specified account number</li>
     *   <li>Throw ResourceNotFoundException if not found</li>
     *   <li>Map entity to response DTO</li>
     *   <li>Return response DTO</li>
     * </ol>
     *
     * @param accountNumber the unique account number to search for (must not be blank)
     * @return a BankAccountResponse containing the account information
     * @throws IllegalArgumentException if accountNumber is null or empty
     * @throws ResourceNotFoundException if no account is found with the specified account number
     */
    public BankAccountResponse getAccountByAccountNumber(String accountNumber) {
        // Validate input
        validateAccount(accountNumber);

        log.info("Retrieving account with account number: {}", accountNumber);

        // Query database for account with specified account number
        BankAccount bankAccount = bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found with account number: {}", accountNumber);
                    return new ResourceNotFoundException("BankAccount", "accountNumber", accountNumber);
                });

        log.debug("Account found with account number: {} - ID: {}", accountNumber, bankAccount.getId());

        // Map entity to response DTO
        BankAccountResponse response = bankAccountMapper.toDto(bankAccount);

        log.info("Successfully retrieved account - Account Number: {}, Holder: {}, ID: {}",
                accountNumber,
                bankAccount.getAccountHolderName(),
                bankAccount.getId());

        return response;
    }

    /**
     * Retrieves all bank accounts with a specific status with pagination support.
     * This method queries the database for all accounts matching the specified status
     * and returns the paginated results mapped to response DTOs.
     *
     * <p>Supported Status Values:</p>
     * <ul>
     *   <li>ACTIVE - Account is active and operational</li>
     *   <li>FROZEN - Account is frozen and cannot process transactions</li>
     *   <li>CLOSED - Account is closed and no longer in use</li>
     * </ul>
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate input status is not null or empty</li>
     *   <li>Validate pagination parameters (page >= 0 and size > 0)</li>
     *   <li>Convert status string to AccountStatus enum</li>
     *   <li>Create Pageable object with specified page and size</li>
     *   <li>Query database for accounts with specified status</li>
     *   <li>Map entities to response DTOs</li>
     *   <li>Return paginated response</li>
     * </ol>
     *
     * @param status the account status to filter by (case-insensitive: ACTIVE, FROZEN, CLOSED)
     * @param page the page number (0-indexed, must be >= 0)
     * @param size the number of records per page (must be > 0)
     * @return a Page of BankAccountResponse objects with the specified status
     * @throws IllegalArgumentException if status is null/empty, page is negative, or size is invalid
     * @throws IllegalArgumentException if status is not a valid AccountStatus enum value
     */
    public Page<BankAccountResponse> getAccountsByStatus(String status, int page, int size) {
        // Validate status parameter
        if (status == null || status.isBlank()) {
            log.error("Failed to retrieve accounts by status: status is null or blank");
            throw new IllegalArgumentException("Account status cannot be null or empty");
        }

        // Validate pagination parameters
        validatePaginationParams(page, size);

        log.info("Retrieving accounts with status: {} - Page: {}, Size: {}",
                status.toUpperCase(), page, size);

        // Convert status string to AccountStatus enum
        AccountStatus accountStatus;
        try {
            accountStatus = AccountStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid account status: {}. Valid values are: ACTIVE, FROZEN, CLOSED", status);
            throw new IllegalArgumentException(
                    "Invalid account status: '" + status + "'. Valid values are: ACTIVE, FROZEN, CLOSED", e);
        }

        log.debug("Converted status string '{}' to enum: {}", status, accountStatus.name());

        // Create Pageable object with specified page and size
        Pageable pageable = Pageable.ofSize(size).withPage(page);

        // Query database for accounts with specified status
        Page<BankAccount> bankAccounts = bankAccountRepository.findByAccountStatus(accountStatus, pageable);

        log.debug("Retrieved {} accounts with status {}. Total pages: {}, Total records: {}",
                bankAccounts.getNumberOfElements(),
                accountStatus.getDisplayName(),
                bankAccounts.getTotalPages(),
                bankAccounts.getTotalElements());

        // Map entities to response DTOs
        Page<BankAccountResponse> response = bankAccounts.map(bankAccountMapper::toDto);

        log.info("Successfully retrieved paginated accounts with status {} - Page: {}, Records returned: {}/{}",
                accountStatus.getDisplayName(),
                page,
                response.getNumberOfElements(),
                size);

        return response;
    }

    /**
     * Retrieves all bank accounts by the account holder's name with pagination support.
     * This method queries the database for all accounts matching the specified holder name
     * and returns the paginated results mapped to response DTOs.
     *
     * <p>Note: Account holder names are not unique, so this method returns all matching accounts.</p>
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate input account holder name is not null or empty</li>
     *   <li>Validate pagination parameters (page >= 0 and size > 0)</li>
     *   <li>Create Pageable object with specified page and size</li>
     *   <li>Query database for accounts with specified holder name</li>
     *   <li>Map entities to response DTOs</li>
     *   <li>Return paginated response</li>
     * </ol>
     *
     * @param accountHolderName the account holder name to search for (must not be blank)
     * @param page the page number (0-indexed, must be >= 0)
     * @param size the number of records per page (must be > 0)
     * @return a Page of BankAccountResponse objects matching the holder name
     * @throws IllegalArgumentException if accountHolderName is null/empty, page is negative, or size is invalid
     */
    public Page<BankAccountResponse> getAccountByHolderName(String accountHolderName, int page, int size) {
        // Validate pagination parameters
        validatePaginationParams(page, size);

        // Validate input
        if (accountHolderName == null || accountHolderName.isBlank()) {
            log.error("Failed to retrieve account: account holder name is null or blank");
            throw new IllegalArgumentException("Account holder name cannot be null or empty");
        }

        log.info("Retrieving account with holder name: {} - Page: {}, Size: {}", accountHolderName, page, size);

        Pageable pageable = Pageable.ofSize(size).withPage(page);
        // Query database for accounts with specified holder name
        Page<BankAccount> bankAccount = bankAccountRepository
                .findByAccountHolderName(accountHolderName, pageable);

        log.debug("Retrieved {} accounts with holder name: {}. Total pages: {}, Total records: {}",
                bankAccount.getNumberOfElements(),
                accountHolderName,
                bankAccount.getTotalPages(),
                bankAccount.getTotalElements());

        // Map entities to response DTOs
        Page<BankAccountResponse> response = bankAccount.map(bankAccountMapper::toDto);

        log.info("Successfully retrieved accounts with holder name: {} - Page: {}, Records returned: {}/{}",
                accountHolderName,
                page,
                response.getNumberOfElements(),
                size);

        return response;
    }

    /**
     * Updates an existing bank account with the provided information.
     * This method retrieves an account by ID, updates its information, and persists the changes to the database.
     *
     * <p>Updateable Fields:</p>
     * <ul>
     *   <li>accountHolderName - The name of the account holder</li>
     *   <li>accountStatus - The current status of the account (ACTIVE, FROZEN, CLOSED)</li>
     *   <li>accountType - The type of account (PERSONAL, BUSINESS)</li>
     * </ul>
     *
     * <p>Non-Updateable Fields:</p>
     * <ul>
     *   <li>id - Account ID (immutable, primary key)</li>
     *   <li>accountNumber - Account number (immutable, unique identifier)</li>
     *   <li>balance - Use dedicated balance update methods instead</li>
     *   <li>createdAt - Creation timestamp (immutable, set at account creation)</li>
     * </ul>
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate input ID is not null and is positive</li>
     *   <li>Validate update request is not null</li>
     *   <li>Query database for account with specified ID</li>
     *   <li>Throw ResourceNotFoundException if not found</li>
     *   <li>Update account fields with values from request DTO</li>
     *   <li>Set current timestamp as updatedAt</li>
     *   <li>Save updated account to database</li>
     *   <li>Map entity to response DTO</li>
     *   <li>Return updated account information</li>
     * </ol>
     *
     * @param id the unique account identifier (must be > 0)
     * @param bankAccountRequest the request DTO containing updated account information
     * @return a BankAccountResponse containing the updated account information
     * @throws IllegalArgumentException if id is null/non-positive or bankAccountRequest is null
     * @throws ResourceNotFoundException if no account is found with the specified ID
     */
    public BankAccountResponse updateAccount(Long id, BankAccountRequest bankAccountRequest) {
        // Validate input ID
        if (id == null) {
            log.error("Failed to update account: id is null");
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        if (id <= 0) {
            log.error("Failed to update account: id {} is not positive", id);
            throw new IllegalArgumentException("Account ID must be greater than 0. Provided: " + id);
        }

        // Validate update request
        if (bankAccountRequest == null) {
            log.error("Failed to update account: bankAccountRequest is null");
            throw new IllegalArgumentException("Bank account request cannot be null");
        }

        log.info("Updating account with ID: {}", id);

        // Query database for existing account
        BankAccount bankAccount = bankAccountRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Account not found with ID: {}", id);
                    return new ResourceNotFoundException("BankAccount", "id", id);
                });

        log.debug("Account found with ID: {} - Current holder: {}", id, bankAccount.getAccountHolderName());

        // Store old values for audit logging
        String oldHolderName = bankAccount.getAccountHolderName();
        AccountType oldType = bankAccount.getAccountType();
        AccountStatus status = bankAccount.getAccountStatus();

        // Update account fields from request DTO
        bankAccount.setAccountHolderName(bankAccountRequest.getAccountHolderName());
        bankAccount.setAccountType(bankAccountRequest.getAccountType());
        bankAccount.setAccountStatus(status);
        bankAccount.setUpdatedAt(LocalDateTime.now());

        log.debug("Account fields updated - Old holder: {}, New holder: {}, Old type: {}, New type: {}",
                oldHolderName,
                bankAccountRequest.getAccountHolderName(),
                oldType.getDisplayName(),
                bankAccountRequest.getAccountType().getDisplayName());

        // Save updated account to database
        BankAccount updatedAccount = bankAccountRepository.save(bankAccount);
        log.debug("Updated account persisted to database with ID: {}", updatedAccount.getId());

        // Map entity to response DTO
        BankAccountResponse response = bankAccountMapper.toDto(updatedAccount);

        log.info("Account updated successfully - ID: {}, Account Number: {}, Holder: {}, Status: {}",
                updatedAccount.getId(),
                updatedAccount.getAccountNumber(),
                updatedAccount.getAccountHolderName(),
                updatedAccount.getAccountStatus().getDisplayName());

        return response;
    }

    public void deleteAccount(Long id) {
        // Validate input ID
        if (id == null) {
            log.error("Failed to delete account: id is null");
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        if (id <= 0) {
            log.error("Failed to delete account: id {} is not positive", id);
            throw new IllegalArgumentException("Account ID must be greater than 0. Provided: " + id);
        }

        log.info("Deleting account with ID: {}", id);

        // Check if account exists before deletion
        if (!bankAccountRepository.existsById(id)) {
            log.warn("Account not found with ID: {}. Cannot delete non-existent account.", id);
            throw new ResourceNotFoundException("BankAccount", "id", id);
        }

        // Perform deletion
        bankAccountRepository.deleteById(id);

        log.info("Account deleted successfully - ID: {}", id);
    }


    @Cacheable(value ="accountBalance"
            , key="#accountNumber"
            , unless = "#result == null")
    public BigDecimal getBalance(String accountNumber)
    {
        //validate input
        validateAccount(accountNumber);

        log.info("Retrieving balance for account number: {}", accountNumber);
        //Query database for account with specified account number
        BigDecimal balance = bankAccountRepository.findBalanceByAccountNumber(accountNumber);

        log.info("Successfully retrieved balance for account number: {} - Balance: {}", accountNumber, balance);

        return balance;
    }

    /**
     * Validates pagination parameters to ensure they are within acceptable ranges.
     * This method can be reused across multiple service methods that support pagination
     * to ensure consistent validation and error handling.
     *
     * <p>Validation Rules:</p>
     * <ul>
     *   <li>Page number must be >= 0 (0-indexed)</li>
     *   <li>Page size must be > 0</li>
     * </ul>
     *
     * @param page the page number (0-indexed)
     * @param size the page size (records per page)
     * @throws IllegalArgumentException if page is negative or size is less than or equal to 0
     */
    private void validatePaginationParams(int page, int size) {
        if (page < 0) {
            log.error("Failed to validate pagination: page number {} is negative", page);
            throw new IllegalArgumentException("Page number cannot be negative. Provided: " + page);
        }

        if (size <= 0) {
            log.error("Failed to validate pagination: page size {} is invalid", size);
            throw new IllegalArgumentException("Page size must be greater than 0. Provided: " + size);
        }
    }

    /**
     * Validates the input account number for operations that require an account number.
     * It checks if the account number is null or blank and throws an IllegalArgumentException if the validation fails.
     * This method can be reused across multiple service methods that require account number validation
     * to ensure consistent error handling and logging.
     *
     * @param accountNumber the account number to validate (must not be blank)
     * @throws IllegalArgumentException if accountNumber is null or blank
     */
    private void validateAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            log.error("Failed to validate account: account number is null or blank");
            throw new IllegalArgumentException("Account number cannot be null or empty");
        }
    }

    /**
     * Freezes a bank account by setting its status to FROZEN.
     * A frozen account cannot process transactions until it is unfrozen.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate input account number is not null or empty</li>
     *   <li>Query database for account with specified account number</li>
     *   <li>Check if account is already frozen</li>
     *   <li>Update account status to FROZEN</li>
     *   <li>Set updated timestamp</li>
     *   <li>Save changes to database</li>
     * </ol>
     *
     * @param accountNumber the account number to freeze (must not be blank)
     * @throws IllegalArgumentException if accountNumber is null or empty
     * @throws ResourceNotFoundException if no account is found with the specified account number
     * @throws IllegalStateException if the account is already frozen
     */
    public void freezeAccount(String accountNumber) {
        validateAccount(accountNumber);

        log.info("Attempting to freeze account with account number: {}", accountNumber);

        BankAccount bankAccount = bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found with account number: {}", accountNumber);
                    return new ResourceNotFoundException("BankAccount", "accountNumber", accountNumber);
                });

        if (bankAccount.getAccountStatus() == AccountStatus.FROZEN) {
            log.warn("Account with account number {} is already frozen", accountNumber);
            throw new IllegalStateException("Account is already frozen");
        }

        bankAccount.setAccountStatus(AccountStatus.FROZEN);
        bankAccount.setUpdatedAt(LocalDateTime.now());
        bankAccountRepository.save(bankAccount);

        log.info("Account frozen successfully - Account Number: {}, Holder: {}",
                accountNumber,
                bankAccount.getAccountHolderName());
    }

    /**
     * Updates the balance of a bank account by adding the specified amount.
     * This method retrieves the account, adds the amount to the current balance,
     * and persists the changes. The cache for this account's balance is invalidated
     * after the update to ensure subsequent reads get the latest value.
     *
     * <p>Process:</p>
     * <ol>
     *   <li>Validate input account number is not null or empty</li>
     *   <li>Validate amount is non-negative</li>
     *   <li>Query database for account with specified account number</li>
     *   <li>Calculate new balance (current balance + amount)</li>
     *   <li>Update account balance and timestamp</li>
     *   <li>Save changes to database</li>
     *   <li>Invalidate cache for this account's balance</li>
     * </ol>
     *
     * @param accountNumber the account number to update (must not be blank)
     * @param amount the amount to add to the balance (must be >= 0)
     * @throws IllegalArgumentException if accountNumber is null/empty or amount is negative
     * @throws ResourceNotFoundException if no account is found with the specified account number
     */
    @CacheEvict(value = "accountBalance", key = "#accountNumber")
    public void updateBalance(String accountNumber, BigDecimal amount) {
        validateAccount(accountNumber);


        log.info("Updating balance for account number: {} with amount: {}", accountNumber, amount);

        BankAccount bankAccount = bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found with account number: {}", accountNumber);
                    return new ResourceNotFoundException("BankAccount", "accountNumber", accountNumber);
                });

        BigDecimal oldBalance = bankAccount.getBalance();
        BigDecimal newBalance = bankAccount.getBalance().add(amount);

        // Prevent negative balance
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.error("Transaction would result in negative balance for account number {}: Old Balance: {}, Amount: {}, New Balance: {}",
                    accountNumber, oldBalance, amount, newBalance);
            throw new IllegalArgumentException("Insufficient balance. Transaction would result in negative balance.");
        }

        bankAccount.setBalance(newBalance);
        bankAccount.setUpdatedAt(LocalDateTime.now());
        bankAccountRepository.save(bankAccount);

        log.info("Balance updated successfully for account number {} - Old Balance: {}, Amount Added: {}, New Balance: {}",
                accountNumber,
                oldBalance,
                amount,
                newBalance);
    }

    /**
     * Finds a bank account entity by account number.
     * This method is used internally by other services that need the BankAccount entity.
     *
     * @param accountNumber the account number to search for
     * @return the BankAccount entity
     * @throws ResourceNotFoundException if account not found
     * @throws IllegalArgumentException if accountNumber is null or blank
     */
    public BankAccount findByAccountNumber(String accountNumber) {
        validateAccount(accountNumber);

        log.debug("Finding BankAccount entity for account number: {}", accountNumber);

        return bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found with account number: {}", accountNumber);
                    return new ResourceNotFoundException("BankAccount", "accountNumber", accountNumber);
                });
    }
}
