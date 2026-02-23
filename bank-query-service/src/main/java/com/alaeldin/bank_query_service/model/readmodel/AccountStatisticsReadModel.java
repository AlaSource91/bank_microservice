package com.alaeldin.bank_query_service.model.readmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

@Document("account_statistics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatisticsReadModel
{
   @Id
   private String id;
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
