package com.alaeldin.bank_query_service.model.readmodel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
@CompoundIndex(name = "account_date_idx", def = "{sourceAccountNumber: 1, transactionDate: -1}")
@CompoundIndex(name = "dest_date_idx", def = "{destinationAccountNumber: 1, transactionDate: -1}")
public class TransactionReadModel {

    @Id
    private String id;

    @Indexed
    private String transactionId;

    @Indexed
    private String sourceAccountNumber;

    private String sourceAccountHolderName;

    @Indexed
    private String destinationAccountNumber;

    private String destinationAccountHolderName;

    private BigDecimal amount;

    private String description;

    @Indexed
    private LocalDateTime transactionDate;

    @Indexed
    private String status;

    private LocalDateTime failedAt;

    private String failureReason;

    private BigDecimal sourceBalanceAfter;

    private BigDecimal destinationBalanceAfter;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
