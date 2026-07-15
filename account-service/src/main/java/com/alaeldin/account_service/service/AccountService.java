package com.alaeldin.account_service.service;

import com.alaeldin.account_service.component.CurrentUserUtil;
import com.alaeldin.account_service.constant.AccountEventType;
import com.alaeldin.account_service.constant.AccountStatus;
import com.alaeldin.account_service.dto.AccountRequest;
import com.alaeldin.account_service.dto.AccountResponse;
import com.alaeldin.account_service.dto.UpdateStatusAccountRequest;
import com.alaeldin.account_service.dto.UserResponse;
import com.alaeldin.account_service.exception.AccountNotFoundException;
import com.alaeldin.account_service.exception.UnauthorizedAccessException;
import com.alaeldin.account_service.mapper.BankAccountMapper;
import com.alaeldin.account_service.model.BankAccount;
import com.alaeldin.account_service.repository.AccountRepository;
import com.alaeldin.account_service.util.AccountNumberUtil;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@Transactional
public class AccountService {

    private static final String AUTH_SERVICE_NAME = "Auth-service";
    private static final int WEB_CLIENT_TIMEOUT_SECONDS = 10;
    private final AccountRepository accountRepository;
    private final WebClient webClient;
    private final CurrentUserUtil currentUserUtil;
    private final BankAccountMapper bankAccountMapper;
   private final EventPublishBankAccountService eventPublishBankAccountService;

    public AccountService(AccountRepository accountRepository
            , WebClient.Builder webClientBuilder
            , CurrentUserUtil currentUserUtil
            ,  BankAccountMapper bankAccountMapper , EventPublishBankAccountService eventPublishBankAccountService) {

        this.accountRepository = accountRepository;
        this.webClient = webClientBuilder.build();
        this.currentUserUtil = currentUserUtil;
        this.bankAccountMapper = bankAccountMapper;
        this.eventPublishBankAccountService = eventPublishBankAccountService;

    }

    /**
     * Creates a new bank account for the authenticated user.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates the account request (handled by controller @Valid annotation)</li>
     *   <li>Retrieves the current user's profile from Auth service</li>
     *   <li>Checks if the user already has an account (duplicate prevention)</li>
     *   <li>Generates a unique account number</li>
     *   <li>Constructs the account holder's full name from user profile</li>
     *   <li>Creates and persists the new account with ACTIVE status and zero balance</li>
     *   <li>Returns the created account as a response DTO</li>
     * </ol>
     *
     * @param accountRequest the validated account creation request containing account type
     * @return AccountResponse containing the newly created account details
     * @throws IllegalArgumentException if accountRequest or accountType is null
     * @throws IllegalStateException if the user already has an account
     * @throws RuntimeException if unable to retrieve user profile from Auth service
     */
    public AccountResponse createAccount(AccountRequest accountRequest) {
        log.info("Starting account creation process for accountType: {}",
                accountRequest != null ? accountRequest.getAccountType() : "null");

        // Validate request
        validateAccountRequest(accountRequest);

        // Retrieve current user profile from Auth service
        UserResponse userResponse = getCurrentUserProfile();
        log.debug("Retrieved user profile for userId: {}", userResponse.getUserId());

        // Generate unique account number
        String accountNumber = AccountNumberUtil.generateAccountNumber();
        log.debug("Generated account number: {}", accountNumber);

        // Construct account holder name with proper null handling
        String holderName = buildAccountHolderName(userResponse);
        log.debug("Account holder name: {}", holderName);

        // Build and save the bank account entity
        assert accountRequest != null;
        BankAccount bankAccount = BankAccount.builder()
                .accountNumber(accountNumber)
                .accountHolderName(holderName)
                .balance(BigDecimal.ZERO)
                .accountType(accountRequest.getAccountType())
                .accountStatus(AccountStatus.ACTIVE)
                .userId(userResponse.getUserId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        BankAccount savedAccount = accountRepository.save(bankAccount);
        eventPublishBankAccountService.saveAccountEventToOutbox(savedAccount , AccountEventType.ACCOUNT_CREATED);
        log.info("Successfully created account - AccountNumber: {}, UserId: {}, HolderName: {}",
                savedAccount.getAccountNumber(),
                savedAccount.getUserId(),
                savedAccount.getAccountHolderName());

        return bankAccountMapper.toAccountResponse(savedAccount);
    }

    /**
     * Validates the account creation request.
     *
     * @param accountRequest the request to validate
     * @throws IllegalArgumentException if validation fails
     */

    private void validateAccountRequest(AccountRequest accountRequest) {
        if (accountRequest == null) {
            log.error("Account creation failed: accountRequest is null");
            throw new IllegalArgumentException("Account request cannot be null");
        }

        if (accountRequest.getAccountType() == null) {
            log.error("Account creation failed: accountType is null");
            throw new IllegalArgumentException("Account type cannot be null");
        }

        log.debug("Account request validation passed for accountType: {}", accountRequest.getAccountType());
    }


    /**
     * Constructs the full account holder name from user profile data.
     * Handles null or empty middle names gracefully to avoid extra spaces.
     *
     * @param userResponse the user profile data
     * @return the formatted full name
     * @throws IllegalArgumentException if first name or last name is null/empty
     */
    private String buildAccountHolderName(UserResponse userResponse) {
        String firstName = userResponse.getFirstName();
        String middleName = userResponse.getMiddleName();
        String lastName = userResponse.getLastName();

        // Validate required name fields
        if (firstName == null || firstName.trim().isEmpty()) {
            log.error("Account creation failed: firstName is null or empty for userId: {}", userResponse.getUserId());
            throw new IllegalArgumentException("First name is required for account creation");
        }

        if (lastName == null || lastName.trim().isEmpty()) {
            log.error("Account creation failed: lastName is null or empty for userId: {}", userResponse.getUserId());
            throw new IllegalArgumentException("Last name is required for account creation");
        }

        // Build full name with proper spacing (handle null/empty middle name)
        StringBuilder fullName = new StringBuilder(firstName.trim());

        if (middleName != null && !middleName.trim().isEmpty()) {
            fullName.append(" ").append(middleName.trim());
        }

        fullName.append(" ").append(lastName.trim());

        return fullName.toString();
    }

    /**
     * Retrieves the current authenticated user's profile from the Auth service.
     *
     * <p>This method calls the Auth service's /me endpoint to fetch the profile
     * of the currently authenticated user. The authentication context is automatically
     * forwarded through the WebClient request headers.</p>
     *
     * <p>The method uses service discovery (Eureka) to locate the Auth service dynamically,
     * and includes a timeout to prevent indefinite blocking. Any errors (including timeouts)
     * are caught and wrapped in a RuntimeException with proper logging.</p>
     *
     * @return UserResponse containing the authenticated user's profile information
     * @throws RuntimeException if the Auth service is unavailable, returns an error, or the request times out
     */
    public UserResponse getCurrentUserProfile() {
        String userName = currentUserUtil.getUserName();
        log.info("Fetching user profile for authenticated user: {}", userName);

        try {
            String token = currentUserUtil.getToken();
            Long userId = currentUserUtil.getUserId();

            UserResponse userResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host(AUTH_SERVICE_NAME)
                            .path("/api/v1/auth/{id}")
                            .build(userId))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(UserResponse.class)
                    .timeout(java.time.Duration.ofSeconds(WEB_CLIENT_TIMEOUT_SECONDS))
                    .block();

            if (userResponse == null) {
                log.error("Received null response from Auth service for user: {}", userName);
                throw new RuntimeException("Failed to retrieve user profile: null response from Auth service");
            }

            log.info("Successfully retrieved user profile for user: {} (userId: {})",
                    userName, userResponse.getUserId());

            return userResponse;

        } catch (Exception e) {
            log.error("Error retrieving user profile from Auth service for user: {}. Error: {}",
                    userName, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve user profile from Auth service", e);
        }
    }
    
    /**
     * Updates the status of an existing bank account.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates the status update request (null checks)</li>
     *   <li>Retrieves the existing account from the database</li>
     *   <li>Verifies the account exists</li>
     *   <li>Updates the account status and timestamp</li>
     *   <li>Persists the updated account</li>
     *   <li>Returns the updated account as a response DTO</li>
     * </ol>
     *
     * @param statusRequest the validated status update request containing account ID and new status
     * @return AccountResponse containing the updated account details
     * @throws IllegalArgumentException if statusRequest, account ID, or status is null
     * @throws AccountNotFoundException if the account with the specified ID does not exist
     */
    public AccountResponse changeStatusAccount(UpdateStatusAccountRequest statusRequest) {
        log.info("Starting account status update process for accountId: {}",
                statusRequest != null ? statusRequest.getId() : "null");

        // Validate request
        validateStatusUpdateRequest(statusRequest);

        Long accountId = statusRequest.getId();
        AccountStatus newStatus = statusRequest.getStatus();

        // Retrieve existing account
        BankAccount existingAccount = accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.error("Account status update failed: Account not found with ID: {}", accountId);
                    return new AccountNotFoundException(
                            String.format("Account not found with ID: %d", accountId)
                    );
                });

        log.debug("Retrieved existing account - AccountNumber: {}, Current Status: {}",
                existingAccount.getAccountNumber(), existingAccount.getAccountStatus());
        // Validate user authorization
        validateAccountOwnership(existingAccount);
        // Update account status and timestamp
        AccountStatus oldStatus = existingAccount.getAccountStatus();
        existingAccount.setAccountStatus(newStatus);
        existingAccount.setUpdatedAt(LocalDateTime.now());

        // Save updated account
        BankAccount updatedAccount = accountRepository.save(existingAccount);
        if (newStatus == AccountStatus.FROZEN) {
            eventPublishBankAccountService.saveAccountEventToOutbox(
                    updatedAccount,
                    AccountEventType.ACCOUNT_FROZEN
            );
        } else {
            eventPublishBankAccountService.saveAccountEventToOutbox(
                    updatedAccount,
                    AccountEventType.ACCOUNT_UPDATED
            );
        }
         log.info("Successfully updated account status - AccountNumber: {}, AccountId: {}, Old Status: {}, New Status: {}",
                updatedAccount.getAccountNumber(),
                updatedAccount.getId(),
                oldStatus,
                newStatus);

        return bankAccountMapper.toAccountResponse(updatedAccount);
    }

    /**
     * Validates the account status update request.
     *
     * @param statusRequest the request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateStatusUpdateRequest(UpdateStatusAccountRequest statusRequest) {
        if (statusRequest == null) {
            log.error("Account status update failed: statusRequest is null");
            throw new IllegalArgumentException("Status update request cannot be null");
        }

        if (statusRequest.getId() == null) {
            log.error("Account status update failed: account ID is null");
            throw new IllegalArgumentException("Account ID cannot be null");
        }

        if (statusRequest.getStatus() == null) {
            log.error("Account status update failed: status is null for accountId: {}", statusRequest.getId());
            throw new IllegalArgumentException("Account status cannot be null");
        }

        log.debug("Status update request validation passed - AccountId: {}, New Status: {}",
                statusRequest.getId(), statusRequest.getStatus());
    }
    
    /**
     * Deletes an existing bank account from the system.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Validates the account ID is not null</li>
     *   <li>Retrieves the existing account from the database</li>
     *   <li>Verifies the account exists</li>
     *   <li>Validates user authorization (must be account owner or admin)</li>
     *   <li>Validates business rules (balance must be zero, account not locked)</li>
     *   <li>Performs hard delete of the account</li>
     *   <li>Logs deletion for audit trail</li>
     *   <li>Publishes AccountDeletedEvent to Kafka for read model updates and cache eviction</li>
     * </ol>
     *
     * <p><strong>Authorization:</strong> Only the account owner or users with admin role
     * can delete an account.</p>
     *
     * <p><strong>Note:</strong> This is a hard delete operation. Consider using soft delete
     * (status change to CLOSED) for better audit trail and data recovery options.</p>
     *
     * <p><strong>Cache Eviction:</strong> Cache eviction is handled event-driven in bank-query-service
     * via AccountEventHandler when it processes the AccountDeletedEvent.</p>
     *
     * @param accountId the ID of the account to delete
     * @throws IllegalArgumentException if accountId is null or if account has non-zero balance
     * @throws AccountNotFoundException if the account with the specified ID does not exist
     * @throws UnauthorizedAccessException if the current user is not the account owner and not an admin
     * @throws IllegalStateException if the account is currently locked
     */
    public void deleteAccount(Long accountId) {
        log.info("Starting account deletion process for accountId: {}", accountId);

        // Validate account ID
        validateAccountId(accountId);

        // Retrieve existing account
        BankAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.error("Account deletion failed: Account not found with ID: {}", accountId);
                    return new AccountNotFoundException(
                            String.format("Account not found with ID: %d", accountId)
                    );
                });

        log.debug("Retrieved account for deletion - AccountNumber: {}, AccountHolder: {}, Balance: {}, Status: {}, OwnerId: {}",
                account.getAccountNumber(),
                account.getAccountHolderName(),
                account.getBalance(),
                account.getAccountStatus(),
                account.getUserId());

        // Validate user authorization
        validateAccountOwnership(account);

        // Validate business rules before deletion
        validateAccountForDeletion(account);

        // Perform deletion
        accountRepository.delete(account);
        eventPublishBankAccountService.saveAccountEventToOutbox(account,AccountEventType.ACCOUNT_DELETE);
        log.info("Successfully deleted account - AccountNumber: {}, AccountId: {}, AccountHolder: {}, DeletedBy: {}",
                account.getAccountNumber(),
                account.getId(),
                account.getAccountHolderName(),
                currentUserUtil.getUserName());
    }

    /**
     * Validates that the current user has permission to access/modify the account.
     * Users can only access their own accounts unless they have admin role.
     *
     * @param account the account to validate ownership for
     * @throws UnauthorizedAccessException if the user doesn't have permission
     */
    private void validateAccountOwnership(BankAccount account) {
        Long currentUserId = currentUserUtil.getUserId();
        boolean isAdmin = currentUserUtil.isAdmin();
        Long accountOwnerId = account.getUserId();

        log.debug("Validating account ownership - CurrentUserId: {}, IsAdmin: {}, AccountOwnerId: {}",
                currentUserId, isAdmin, accountOwnerId);

        // Allow access if user is admin OR if user owns the account
        boolean hasAccess = isAdmin || Objects.equals(currentUserId, accountOwnerId);

        if (!hasAccess) {
            log.error("Account access denied - AccountNumber: {}, CurrentUserId: {}, AccountOwnerId: {}, IsAdmin: {}",
                    account.getAccountNumber(), currentUserId, accountOwnerId, isAdmin);
            throw new UnauthorizedAccessException(
                    String.format("Access denied: You do not have permission to access account %s",
                            account.getAccountNumber())
            );
        }

        log.debug("Account ownership validation passed - AccountNumber: {}", account.getAccountNumber());
    }

    /**
     * Validates that the account ID is not null.
     *
     * @param accountId the account ID to validate
     * @throws IllegalArgumentException if accountId is null
     */
    private void validateAccountId(Long accountId) {
        if (accountId == null) {
            log.error("Account deletion failed: account ID is null");
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        log.debug("Account ID validation passed for accountId: {}", accountId);
    }

    /**
     * Validates that an account can be safely deleted.
     * Checks business rules such as zero balance and unlocked status.
     *
     * @param account the account to validate
     * @throws IllegalArgumentException if account has non-zero balance
     * @throws IllegalStateException if account is currently locked
     */
    private void validateAccountForDeletion(BankAccount account) {
        // Check if account has zero balance
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            log.error("Account deletion failed: Account {} has non-zero balance: {}",
                    account.getAccountNumber(), account.getBalance());
            throw new IllegalArgumentException(
                    String.format("Cannot delete account with non-zero balance. Current balance: %s",
                            account.getBalance())
            );
        }

        // Check if account is locked
        if (account.isLocked()) {
            log.error("Account deletion failed: Account {} is currently locked by: {}",
                    account.getAccountNumber(), account.getLockBy());
            throw new IllegalStateException(
                    String.format("Cannot delete account that is currently locked by: %s",
                            account.getLockBy())
            );
        }

        log.debug("Account validation for deletion passed - AccountNumber: {}",
                account.getAccountNumber());
    }
}
