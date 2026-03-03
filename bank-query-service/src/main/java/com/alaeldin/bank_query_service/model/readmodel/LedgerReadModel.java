package com.alaeldin.bank_query_service.model.readmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "ledgers")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LedgerReadModel {

    @Id
    private String id;
    @Indexed
    private String accountNumber;
    @Indexed
    private String ledgerEntryId;
    @Indexed
    private String transactionReference;
    private String entryType;
    private String amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private LocalDateTime entryDate;
    private String description;



}
