package com.alaeldin.bank_query_service.controller;

import com.alaeldin.bank_query_service.dto.AccountQueryResponse;
import com.alaeldin.bank_query_service.dto.PageResponse;
import com.alaeldin.bank_query_service.service.AccountQueryService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/**
 * REST Controller for account query operations (CQRS read-side).
 * Provides endpoints to retrieve account information from the read model.
 * All responses are cached for improved performance.
 */
@RestController
@RequestMapping("/api/v1/query/accounts")
@Slf4j
@RequiredArgsConstructor
@Validated
public class AccountQueryController {

    private final AccountQueryService accountQueryService;

    /**
     * Retrieves account details by account number.
     *
     * @param accountNumber the unique account number (alphanumeric)
     * @return ResponseEntity containing account details
     */
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountQueryResponse> getAccount(
            @PathVariable("accountNumber")
            @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "Account number must be alphanumeric and 3-20 characters")
            String accountNumber
    ) {
        log.info("GET /api/v1/query/accounts/{} - Retrieving account details", accountNumber);

        AccountQueryResponse response = accountQueryService.getAccountByNumber(accountNumber);

        return ResponseEntity.ok(response);
    }

    /**
     * Searches accounts by account holder name with pagination.
     *
     * @param accountHolderName the name to search for (case-insensitive)
     * @param page              page number (0-indexed)
     * @param size              page size (max 100)
     * @return Page of accounts matching the search criteria
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<AccountQueryResponse>> searchAccountsByHolderName(
            @RequestParam("holderName")
            @Pattern( regexp = "^\\p{L}[\\p{L}\\s'\\-.]{1,99}$",
                    message = "Holder name must be 2-100 characters and contain letters with optional spaces, hyphens, apostrophes, or dots"
            )
            String accountHolderName,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be 0 or greater")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @jakarta.validation.constraints.Max(value = 100, message = "Page size cannot exceed 100")
            int size
    ) {
        log.info("GET /api/v1/query/accounts/search - HolderName: '{}', Page: {}, Size: {}",
                accountHolderName, page, size);

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<AccountQueryResponse> response = accountQueryService.searchAccountByHolderName(accountHolderName, pageable);

        log.debug("Search results - TotalElements: {}, TotalPages: {}",
                response.getTotalElements(), response.getTotalPages());

        return ResponseEntity.ok(response);
    }
    /**
     * Retrieves all accounts with pagination and sorting.
     *
     * @param page    page number (0-indexed)
     * @param size    page size (max 100)
     * @param sortBy  field to sort by
     * @param sortDir sort direction (asc or desc)
     * @return Page of all accounts
     */
    @GetMapping
    public ResponseEntity<PageResponse<AccountQueryResponse>> getAllAccounts(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be 0 or greater")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @jakarta.validation.constraints.Max(value = 100, message = "Page size cannot exceed 100")
            int size,
            @RequestParam(defaultValue = "createdAt")
            @Pattern(regexp = "^(createdAt|updatedAt|accountNumber|balance|accountHolderName)$",
                    message = "Invalid sort field. Allowed: createdAt, updatedAt, accountNumber, balance, accountHolderName")
            String sortBy,
            @RequestParam(defaultValue = "desc")
            @Pattern(regexp = "^(asc|desc)$", message = "Sort direction must be 'asc' or 'desc'")
            String sortDir
    ) {
        log.info("GET /api/v1/query/accounts - Page: {}, Size: {}, SortBy: {}, SortDir: {}",
                page, size, sortBy, sortDir);

        Sort.Direction direction = Sort.Direction.fromString(sortDir);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        PageResponse<AccountQueryResponse> response = accountQueryService.getAllAccounts(pageable);

        log.debug("Retrieved accounts - TotalElements: {}, TotalPages: {}",
                response.getTotalElements(), response.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the balance of an account by account number.
     *
     * @param accountNumber the unique account number
     * @return ResponseEntity containing the account balance
     */
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable("accountNumber")
            @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "Account number must be alphanumeric and 3-20 characters")
            String accountNumber
    ) {
        log.info("GET /api/v1/query/accounts/{}/balance", accountNumber);

        BigDecimal balance = accountQueryService.getAccountBalance(accountNumber);

        log.debug("Retrieved balance - AccountNumber: {}, Balance: {}", accountNumber, balance);

        return ResponseEntity.ok(balance);
    }

}
