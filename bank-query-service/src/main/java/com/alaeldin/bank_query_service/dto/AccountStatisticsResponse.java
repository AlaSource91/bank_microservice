package com.alaeldin.bank_query_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AccountStatisticsResponse
{
    private String accountNumber;
    private LocalDate date;
    private Long transactionCount;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal netFlow;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private LocalDate calculatedAt;
}
