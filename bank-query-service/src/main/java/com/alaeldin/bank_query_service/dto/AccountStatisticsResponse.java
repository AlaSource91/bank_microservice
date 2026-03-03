package com.alaeldin.bank_query_service.dto;

import com.alaeldin.bank_query_service.model.readmodel.AccountStatisticsReadModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private LocalDateTime calculatedAt;

    public static AccountStatisticsResponse fromReadModel(AccountStatisticsReadModel readModel)
    {
        if (readModel == null)
        {
            return null;
        }

        return AccountStatisticsResponse
                .builder()
                .accountNumber(readModel.getAccountNumber())
                .date(readModel.getDate())
                .transactionCount(readModel.getTransactionCount())
                .totalDebit(readModel.getTotalDebit())
                .totalCredit(readModel.getTotalCredit())
                .netFlow(readModel.getNetFlow())
                .openingBalance(readModel.getOpeningBalance())
                .closingBalance(readModel.getClosingBalance())
                .calculatedAt(readModel.getCalculatedAt())
                .build();
    }
}
