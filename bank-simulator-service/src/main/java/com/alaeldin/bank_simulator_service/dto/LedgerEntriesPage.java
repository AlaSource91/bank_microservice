package com.alaeldin.bank_simulator_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for ledger entries page - optimized for Redis caching.
 *
 * This DTO avoids the serialization issues of Spring Data Page objects
 * by providing a simple, cacheable structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntriesPage {

    private List<LedgerEntryDto> entries;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerEntryDto {
        private String ledgerEntryId;
        private String transactionReference;
        private String accountNumber;
        private String entryType;
        private BigDecimal amount;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
        private LocalDateTime entryDate;
        private String description;
    }
}
