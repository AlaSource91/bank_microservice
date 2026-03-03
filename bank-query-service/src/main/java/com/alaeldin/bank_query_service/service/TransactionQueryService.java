package com.alaeldin.bank_query_service.service;

import com.alaeldin.bank_query_service.dto.PageResponse;
import com.alaeldin.bank_query_service.dto.TransactionSearchRequest;
import com.alaeldin.bank_query_service.dto.TransactionSearchResponse;
import com.alaeldin.bank_query_service.exception.TransactionNotFoundException;
import com.alaeldin.bank_query_service.mapper.PageResponseMapper;
import com.alaeldin.bank_query_service.model.readmodel.TransactionReadModel;
import com.alaeldin.bank_query_service.repository.TransactionReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for querying transaction information from the read model (CQRS read-side).
 * Provides paginated and cached access to transaction data stored in MongoDB.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionQueryService {

    private final TransactionReadModelRepository transactionReadModelRepository;

    /**
     * Retrieves the transaction history involving either the source or destination account number.
     * Results are cached to improve performance for frequently accessed accounts.
     * @param accountNumber      the source account number to filter transactions
     * @param pageable                 the pagination information for the transaction history
     * @return a paginated response containing transaction search results
     * @throws IllegalArgumentException if either account number is null or empty, or if pageable is invalid
     */
    @Cacheable(
            value = "transactionHistory",
            key = "#accountNumber.trim().toUpperCase() + '_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort"
    )
    public PageResponse<TransactionSearchResponse> getTransactionHistory(
            String accountNumber,
            Pageable pageable
    ) {
        validateAccountNumbers(accountNumber);
        validatePageable(pageable);

        String normalizedAccountNumber = accountNumber.trim().toUpperCase();

        log.debug("Querying transaction history - sourceAccount: {}, page: {}",
                normalizedAccountNumber, pageable.getPageNumber());

        Page<TransactionReadModel> transactionPage = transactionReadModelRepository
                .findBySourceAccountNumberOrDestinationAccountNumber(normalizedAccountNumber, normalizedAccountNumber, pageable);

        Page<TransactionSearchResponse> responsePage = transactionPage
                .map(TransactionSearchResponse::fromTransactionReadModel);

        log.info("Retrieved transaction history - sourceAccount: {}, page: {}, totalElements: {}",
                normalizedAccountNumber, pageable.getPageNumber(), responsePage.getTotalElements());

        return PageResponseMapper.from(responsePage);
    }

    /**
     * Retrieves a single transaction by its unique transaction ID.
     *
     * @param transactionId the unique transaction identifier
     * @return the transaction details
     * @throws IllegalArgumentException   if transactionId is null or empty
     * @throws TransactionNotFoundException if no transaction exists with the given ID
     */
    @Cacheable(value = "transactions", key = "#transactionId.trim().toUpperCase()")
    public TransactionSearchResponse getTransactionByTransactionId(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            log.error("Invalid transactionId provided: {}", transactionId);
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }

        log.debug("Querying transaction by transactionId: {}", transactionId);

        TransactionReadModel transactionReadModel = transactionReadModelRepository
                .findByTransactionId(transactionId)
                .orElseThrow(() -> {
                    log.warn("Transaction not found for transactionId: {}", transactionId);
                    return new TransactionNotFoundException(
                            String.format("Transaction not found with ID: %s", transactionId)
                    );
                });

        log.info("Retrieved transaction - transactionId: {}, sourceAccount: {}, destinationAccount: {}, amount: {}",
                transactionId,
                transactionReadModel.getSourceAccountNumber(),
                transactionReadModel.getDestinationAccountNumber(),
                transactionReadModel.getAmount());

        return TransactionSearchResponse.fromTransactionReadModel(transactionReadModel);
    }

    /**
     * Searches for transactions based on the provided search criteria.
     * If both startDate and endDate are provided, results are filtered by that date range.
     * If dates are omitted, all transactions are returned (paginated).
     *
     * @param request  the transaction search request containing optional date range criteria
     * @param pageable the pagination information for the search results
     * @return a paginated response containing transaction search results
     * @throws IllegalArgumentException if pageable is invalid, or if only one date boundary is provided,
     *                                  or if startDate is after endDate
     */
    @Cacheable(value = "searchTransactions", key = "'search_' + #request.startDate + '_' + #request.endDate + '_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort")
    public PageResponse<TransactionSearchResponse> searchTransactions(
            TransactionSearchRequest request,
            Pageable pageable
    ) {
        validatePageable(pageable);

        LocalDateTime startDate = request.getStartDate();
        LocalDateTime endDate = request.getEndDate();

        // If both dates are provided, validate the range and apply date filter
        if (startDate != null && endDate != null) {
            validateDateRange(startDate, endDate);

            log.info("Searching transactions by date range - startDate: {}, endDate: {}, page: {}",
                    startDate, endDate, pageable.getPageNumber());

            Page<TransactionReadModel> transactionPage = transactionReadModelRepository
                    .findByTransactionDateBetween(startDate, endDate, pageable);
            Page<TransactionSearchResponse> responsePage = transactionPage
                    .map(TransactionSearchResponse::fromTransactionReadModel);

            log.info("Date-range search complete - totalElements: {}", responsePage.getTotalElements());
            return PageResponseMapper.from(responsePage);
        }

        // If only one boundary is provided, reject the request
        if (startDate != null || endDate != null) {
            log.warn("Partial date range provided - startDate: {}, endDate: {}", startDate, endDate);
            throw new IllegalArgumentException("Both startDate and endDate must be provided together, or neither");
        }

        // No date filter — return all transactions
        log.info("Searching all transactions (no date filter) - page: {}", pageable.getPageNumber());
        Page<TransactionReadModel> transactionPage = transactionReadModelRepository.findAll(pageable);
        Page<TransactionSearchResponse> responsePage = transactionPage
                .map(TransactionSearchResponse::fromTransactionReadModel);

        log.info("Unrestricted search complete - totalElements: {}", responsePage.getTotalElements());
        return PageResponseMapper.from(responsePage);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that both source and destination account numbers are non-null and non-empty.
     *
     * @throws IllegalArgumentException if either account number is null or blank
     */
    private void validateAccountNumbers(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            log.error("Invalid  Account number provided: {}", accountNumber);
            throw new IllegalArgumentException(" Account number cannot be null or empty");
        }

    }

    /**
     * Validates the pageable input.
     *
     * @throws IllegalArgumentException if pageable is null or has a negative page number or non-positive page size
     */
    private void validatePageable(Pageable pageable) {
        if (pageable == null) {
            log.error("Pageable is null");
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        if (pageable.getPageNumber() < 0) {
            log.error("Invalid page number: {}", pageable.getPageNumber());
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (pageable.getPageSize() <= 0) {
            log.error("Invalid page size: {}", pageable.getPageSize());
            throw new IllegalArgumentException("Page size must be greater than zero");
        }
    }

    /**
     * Validates that startDate is not after endDate.
     *
     * @throws IllegalArgumentException if startDate is after endDate
     */
    private void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: startDate {} is after endDate {}", startDate, endDate);
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }
}

