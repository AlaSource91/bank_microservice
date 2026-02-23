package com.alaeldin.bank_query_service.service;

import com.alaeldin.bank_query_service.dto.AccountQueryResponse;
import com.alaeldin.bank_query_service.dto.PageResponse;
import com.alaeldin.bank_query_service.exception.AccountNotFoundException;
import com.alaeldin.bank_query_service.mapper.PageResponseMapper;
import com.alaeldin.bank_query_service.model.readmodel.AccountReadModel;
import com.alaeldin.bank_query_service.repository.AccountReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * Service for querying account information from the read model (CQRS read-side).
 * Provides cached access to account data stored in MongoDB.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountReadModelRepository accountReadModelRepository;

    /**
     * Retrieves account details by account number from the read model.
     * Results are cached to improve performance.
     *
     * @param accountNumber the unique account number to query
     * @return AccountQueryResponse containing account details
     * @throws IllegalArgumentException if accountNumber is null or empty
     * @throws AccountNotFoundException if account is not found
     */
    @Cacheable(value = "accountDetails", key = "#accountNumber.trim().toUpperCase()")
    public AccountQueryResponse getAccountByNumber(String accountNumber) {
        // Validate input
        validateAccountNumber(accountNumber);

        log.debug("Querying account details from read model - AccountNumber: {}", accountNumber);
        String normalizedAccountNumber = accountNumber.trim().toUpperCase();

        // Query read model
        AccountReadModel accountReadModel = accountReadModelRepository.findByAccountNumber(normalizedAccountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found in read model - AccountNumber: {}", accountNumber);
                    return new AccountNotFoundException(
                            String.format("Account not found with number: %s", accountNumber)
                    );
                });

        // Convert to DTO
        AccountQueryResponse response = AccountQueryResponse.fromReadModel(accountReadModel);

        log.info("Successfully retrieved account details - AccountNumber: {}, HolderName: {}, Balance: {}",
                accountNumber,
                accountReadModel.getAccountHolderName(),
                accountReadModel.getBalance());

        return response;
    }

    /**
     * Validates the account number input.
     *
     * @param accountNumber the account number to validate
     * @throws IllegalArgumentException if accountNumber is null or empty
     */
    private void validateAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            log.error("Invalid account number provided: {}", accountNumber);
            throw new IllegalArgumentException("Account number cannot be null or empty");
        }
    }

    /**
     * Searches for accounts by account holder name with pagination support.
     * Search is case-insensitive.
     *
     * @param accountHolderName the name of the account holder to search for
     * @param pageable pagination information (page number, page size, sorting)
     * @return a page of AccountQueryResponse matching the search criteria
     * @throws IllegalArgumentException if accountHolderName is null or empty, or if pageable is invalid
     */
    @Cacheable(value = "accountSearch", key = "#accountHolderName.trim().toLowerCase() + '-' + #pageable.pageNumber + '-' + #pageable.pageSize",  unless = "#result == null || #result.isEmpty()")
    public PageResponse<AccountQueryResponse> searchAccountByHolderName(String accountHolderName, Pageable pageable) {
        validateHolderName(accountHolderName);
        validatePageable(pageable);

        String normalizedName = accountHolderName.trim();
        log.debug("Searching accounts by holder name - Name: '{}', Page: {}, Size: {}",
                normalizedName, pageable.getPageNumber(), pageable.getPageSize());

        Page<AccountReadModel> accountReadModels = accountReadModelRepository
                .findByAccountHolderNameContainingIgnoreCase(normalizedName, pageable);

        log.info("Search completed - Name: '{}', Found: {} accounts, Page: {}/{}",
                normalizedName, accountReadModels.getTotalElements(),
                pageable.getPageNumber() + 1, accountReadModels.getTotalPages());

        // Map AccountReadModel to AccountQueryResponse
        Page<AccountQueryResponse> accountResponses = accountReadModels.map(AccountQueryResponse::fromReadModel);

        return PageResponseMapper.from(accountResponses);
    }


    /**
     * Validates the account holder name input.
     *
     * @param accountHolderName the account holder name to validate
     * @throws IllegalArgumentException if accountHolderName is null or empty
     */
    private void validateHolderName(String accountHolderName) {
        if (!StringUtils.hasText(accountHolderName)) {
            log.error("Invalid account holder name provided: {}", accountHolderName);
            throw new IllegalArgumentException("Account holder name cannot be null or empty");
        }
    }

    /**
     * Validates the pageable input for pagination.
     *
     * @param pageable the pageable object to validate
     * @throws IllegalArgumentException if pageable is null, has a negative page number, or has a non-positive page size
     */
    private void validatePageable(Pageable pageable) {
        if (pageable == null) {
            log.error("Invalid pageable provided: null");
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        if (pageable.getPageNumber() < 0) {
            log.error("Invalid page number provided: {}", pageable.getPageNumber());
            throw new IllegalArgumentException("Page number must be 0 or greater");
        }
        if (pageable.getPageSize() <= 0) {
            log.error("Invalid page size provided: {}", pageable.getPageSize());
            throw new IllegalArgumentException("Page size must be greater than 0");
        }
    }

    /**
     * Retrieves all accounts with pagination and sorting.
     * Results are cached to improve performance.
     *
     * @param pageable pagination information (page number, page size, sorting)
     * @return a page of AccountQueryResponse containing all accounts
     * @throws IllegalArgumentException if pageable is invalid
     */
    @Cacheable(value = "allAccounts", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort", unless = "#result == null || #result.content.isEmpty()")
    public PageResponse<AccountQueryResponse> getAllAccounts(Pageable pageable) {
        validatePageable(pageable);

        log.debug("Retrieving all accounts - Page: {}, Size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<AccountReadModel> accountReadModels = accountReadModelRepository.findAll(pageable);

        log.info("Retrieved all accounts - TotalElements: {}, Page: {}/{}",
                accountReadModels.getTotalElements(),
                pageable.getPageNumber() + 1,
                accountReadModels.getTotalPages());

        // Map AccountReadModel to AccountQueryResponse
        Page<AccountQueryResponse> accountResponses = accountReadModels.map(AccountQueryResponse::fromReadModel);

        return PageResponseMapper.from(accountResponses);

    }

    /**
     * Retrieves the account balance for a given account number.
     * Results are cached to improve performance.
     *
     * @param accountNumber the unique account number to query
     * @return the account balance as BigDecimal
     * @throws IllegalArgumentException if accountNumber is null or empty
     * @throws AccountNotFoundException if account is not found
     */
    @Cacheable(value = "accountBalance", key = "#accountNumber.trim().toUpperCase()")
    public BigDecimal getAccountBalance(String accountNumber) {
        validateAccountNumber(accountNumber);

        log.debug("Querying account balance from read model - AccountNumber: {}", accountNumber);

        String normalizedAccountNumber = accountNumber.trim().toUpperCase();
        AccountReadModel accountReadModel = accountReadModelRepository.findByAccountNumber(normalizedAccountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found for balance query - AccountNumber: {}", accountNumber);
                    return new AccountNotFoundException(
                            String.format("Account not found with number: %s", accountNumber)
                    );
                });

        BigDecimal balance = accountReadModel.getBalance();
        log.info("Retrieved account balance - AccountNumber: {}, Balance: {}", accountNumber, balance);

        return balance;
    }
}


