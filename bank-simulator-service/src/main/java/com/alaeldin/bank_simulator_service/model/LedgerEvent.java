package com.alaeldin.bank_simulator_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Immutable
@Builder
public class LedgerEvent {

    private String eventId;
    private String eventType;  // Added to match BaseEvent structure
    private String transactionReference;
    private String accountNumber;
    private String entryType;
    private BigDecimal amount;
    private String entryDate;
    private String ledgerEntryId;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String description;

}
