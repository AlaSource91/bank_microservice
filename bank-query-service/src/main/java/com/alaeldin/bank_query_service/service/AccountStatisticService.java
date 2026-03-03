package com.alaeldin.bank_query_service.service;

import com.alaeldin.bank_query_service.dto.AccountStatisticsResponse;
import com.alaeldin.bank_query_service.dto.PageResponse;
import com.alaeldin.bank_query_service.mapper.PageResponseMapper;
import com.alaeldin.bank_query_service.model.readmodel.AccountStatisticsReadModel;
import com.alaeldin.bank_query_service.repository.AccountStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * Service for querying account statistics from the read model (CQRS read-side).
 * Provides paginated access to daily account statistics stored in MongoDB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatisticService {

    private final AccountStatisticsRepository accountStatisticsRepository;

    /**
     * Retrieves paginated account statistics for a given account number within a date range,
     * ordered by date descending.
     *
     * @param accountNumber the account number to query
     * @param startDate     the start of the date range (inclusive)
     * @param endDate       the end of the date range (inclusive)
     * @param pageable      pagination information (page number, page size, sorting)
     * @return a paginated response of {@link AccountStatisticsResponse}
     * @throws IllegalArgumentException if any input is invalid
     */
    public PageResponse<AccountStatisticsResponse> findByAccountNumberAndDateBetweenOrderByDateDesc(
            String accountNumber, LocalDate startDate, LocalDate endDate, Pageable pageable) {

        validateAccountNumber(accountNumber);
        validateDateRange(startDate, endDate);
        validatePageable(pageable);

        log.debug("Querying account statistics - accountNumber={}, startDate={}, endDate={}, page={}, size={}",
                accountNumber, startDate, endDate, pageable.getPageNumber(), pageable.getPageSize());

        Page<AccountStatisticsReadModel> page = accountStatisticsRepository
                .findByAccountNumberAndDateBetweenOrderByDateDesc(accountNumber, startDate, endDate, pageable);

        log.info("Account statistics query completed - accountNumber={}, resultCount={}, totalElements={}",
                accountNumber, page.getNumberOfElements(), page.getTotalElements());

        Page<AccountStatisticsResponse> responsePages = page.map(AccountStatisticsResponse::fromReadModel);

        return PageResponseMapper.from(responsePages);
    }

    /**
     * Validates that the account number is not null or blank.
     *
     * @param accountNumber the account number to validate
     * @throws IllegalArgumentException if the account number is null or blank
     */
    private void validateAccountNumber(String accountNumber) {
        if (!StringUtils.hasText(accountNumber)) {
            log.error("Invalid account number provided: null or blank");
            throw new IllegalArgumentException("Account number must not be null or blank");
        }
    }

    /**
     * Validates that the date range is non-null and logically ordered.
     *
     * @param startDate the start date
     * @param endDate   the end date
     * @throws IllegalArgumentException if either date is null or startDate is after endDate
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date must not be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    String.format("Start date '%s' must not be after end date '%s'", startDate, endDate));
        }
    }

    /**
     * Validates pageable parameters.
     *
     * @param pageable the pageable to validate
     * @throws IllegalArgumentException if pageable is null, has a negative page number, or non-positive page size
     */
    private void validatePageable(Pageable pageable) {
        if (pageable == null) {
            log.error("Invalid pageable provided: null");
            throw new IllegalArgumentException("Pageable must not be null");
        }
        if (pageable.getPageNumber() < 0) {
            log.error("Invalid page number: {}. Page number must be non-negative.", pageable.getPageNumber());
            throw new IllegalArgumentException("Page number must be non-negative");
        }
        if (pageable.getPageSize() <= 0) {
            log.error("Invalid page size: {}. Page size must be greater than 0.", pageable.getPageSize());
            throw new IllegalArgumentException("Page size must be greater than 0");
        }
    }
}


