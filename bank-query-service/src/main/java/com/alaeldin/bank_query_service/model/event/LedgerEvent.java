package com.alaeldin.bank_query_service.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LedgerEvent  extends BaseEvent{


    private String transactionReference;
    private String entryType;
    private String amount;
    private LocalDateTime entryDate;
    private String ledgerEntryId;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String description;
}
