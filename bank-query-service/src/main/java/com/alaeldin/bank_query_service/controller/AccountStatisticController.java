package com.alaeldin.bank_query_service.controller;

import com.alaeldin.bank_query_service.dto.AccountStatisticsResponse;
import com.alaeldin.bank_query_service.dto.PageResponse;
import com.alaeldin.bank_query_service.service.AccountStatisticService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST Controller for account statistics query operations (CQRS read-side).
 * Provides endpoints to retrieve paginated daily account statistics from the read model.
 */
@RestController
@RequestMapping("/api/v1/query/account-statistics")
@Slf4j
@RequiredArgsConstructor
@Validated
public class AccountStatisticController {

    private final AccountStatisticService accountStatisticService;

    /**
     * Retrieves paginated account statistics for a given account number within a date range,
     * ordered by date descending.
     *
     * @param accountNumber the account number to query (alphanumeric, 3–20 characters)
     * @param startDate     the start of the date range (inclusive), ISO format (yyyy-MM-dd)
     * @param endDate       the end of the date range (inclusive), ISO format (yyyy-MM-dd)
     * @param page          page number (0-indexed, default 0)
     * @param size          page size (1–100, default 20)
     * @return paginated account statistics
     */
    @GetMapping("/{accountNumber}")
    public ResponseEntity<PageResponse<AccountStatisticsResponse>> findByAccountNumberAndDateBetween(
            @PathVariable("accountNumber")
            @Pattern(regexp = "^[A-Za-z0-9]{3,20}$",
                    message = "Account number must be alphanumeric and 3-20 characters")
            String accountNumber,

            @RequestParam("startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam("endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be 0 or greater")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size cannot exceed 100")
            int size
    ) {
        log.info("GET /api/v1/query/account-statistics/{} - startDate={}, endDate={}, page={}, size={}",
                accountNumber, startDate, endDate, page, size);

        PageResponse<AccountStatisticsResponse> response =
                accountStatisticService.findByAccountNumberAndDateBetweenOrderByDateDesc(
                        accountNumber, startDate, endDate, PageRequest.of(page, size));

        log.debug("Statistics query completed - accountNumber={}, totalElements={}, totalPages={}",
                accountNumber, response.getTotalElements(), response.getTotalPages());

        return ResponseEntity.ok(response);
    }
}


