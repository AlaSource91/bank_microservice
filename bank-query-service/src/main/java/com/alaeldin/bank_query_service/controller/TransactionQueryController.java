package com.alaeldin.bank_query_service.controller;

import com.alaeldin.bank_query_service.dto.PageResponse;
import com.alaeldin.bank_query_service.dto.TransactionSearchRequest;
import com.alaeldin.bank_query_service.dto.TransactionSearchResponse;
import com.alaeldin.bank_query_service.service.TransactionQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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


/**
 * REST Controller for transaction query operations (CQRS read-side).
 * Provides endpoints to retrieve and search transaction data from the read model.
 */
@RestController
@RequestMapping("/api/v1/query/transactions")
@Slf4j
@RequiredArgsConstructor
@Validated
public class TransactionQueryController {

    private final TransactionQueryService transactionQueryService;

    /**
     * Retrieves paginated transaction history for a given account number.
     * Returns all transactions where the account is either the source or destination.
     *
     * @param accountNumber the account number (alphanumeric, 3-20 characters)
     * @param page          page number (0-indexed)
     * @param size          page size (1-100)
     * @return paginated list of transactions for the account
     */
    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<PageResponse<TransactionSearchResponse>> getTransactionHistory(
            @PathVariable("accountNumber")
            @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "Account number must be alphanumeric and 3-20 characters")
            String accountNumber,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be 0 or greater")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size cannot exceed 100")
            int size
    ) {
        log.info("GET /api/v1/query/transactions/history/{} - page: {}, size: {}", accountNumber, page, size);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("transactionDate").descending()
        );
        PageResponse<TransactionSearchResponse> response = transactionQueryService.getTransactionHistory(accountNumber, pageable);

        log.debug("Transaction history retrieved - account: {}, totalElements: {}, totalPages: {}",
                accountNumber, response.getTotalElements(), response.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a single transaction by its unique transaction ID.
     *
     * @param transactionId the transaction UUID (36-character alphanumeric with hyphens)
     * @return transaction details
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionSearchResponse> getTransactionById(
            @PathVariable("transactionId")
            @Pattern(
                    regexp = "^BANK_REF_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "Transaction ID must be a valid UUID"
            )
            String transactionId
    ) {
        log.info("GET /api/v1/query/transactions/{} - Retrieving transaction details", transactionId);

        TransactionSearchResponse response = transactionQueryService.getTransactionByTransactionId(transactionId);

        log.debug("Transaction retrieved successfully - transactionId: {}", transactionId);

        return ResponseEntity.ok(response);
    }

    /**
     * Searches transactions based on optional date range criteria with pagination.
     * If both startDate and endDate are provided, results are filtered by that range.
     * If neither date is provided, all transactions are returned (paginated).
     *
     * @param searchRequest search criteria containing optional startDate and endDate
     * @param page          page number (0-indexed)
     * @param size          page size (1-100)
     * @return paginated list of matching transactions
     */
    @PostMapping("/search")
    public ResponseEntity<PageResponse<TransactionSearchResponse>> searchTransactions(
            @Valid @RequestBody TransactionSearchRequest searchRequest,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be 0 or greater")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size cannot exceed 100")
            int size
    ) {
        log.info("POST /api/v1/query/transactions/search - criteria: {}, page: {}, size: {}", searchRequest, page, size);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("transactionDate").descending()
        );
        PageResponse<TransactionSearchResponse> response = transactionQueryService.searchTransactions(searchRequest, pageable);

        log.debug("Transaction search completed - totalElements: {}, totalPages: {}",
                response.getTotalElements(), response.getTotalPages());

        return ResponseEntity.ok(response);
    }
}
