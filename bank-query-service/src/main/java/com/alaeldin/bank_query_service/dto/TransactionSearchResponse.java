package com.alaeldin.bank_query_service.dto;

import com.alaeldin.bank_query_service.model.readmodel.AccountReadModel;
import com.alaeldin.bank_query_service.model.readmodel.TransactionReadModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionSearchResponse {

    private String transactionId;
    private String sourceAccountNumber;
    private String sourceAccountHolderName;
    private String destinationAccountNumber;
    private String destinationAccountHolderName;
    private BigDecimal amount;
    private String description;
    private String transactionDate;
    private String status;
    private String failedAt;
    private String failureReason;
    private BigDecimal sourceBalanceAfter;
    private BigDecimal destinationBalanceAfter;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;


    public static TransactionSearchResponse fromTransactionReadModel(TransactionReadModel transactionReadModel)
    {
              if (transactionReadModel == null) {
                  return null;
              }

              return TransactionSearchResponse
                      .builder()
                        .transactionId(transactionReadModel.getTransactionId())
                        .sourceAccountNumber(transactionReadModel.getSourceAccountNumber())
                        .sourceAccountHolderName(transactionReadModel.getSourceAccountHolderName())
                        .destinationAccountNumber(transactionReadModel.getDestinationAccountNumber())
                        .destinationAccountHolderName(transactionReadModel.getDestinationAccountHolderName())
                        .amount(transactionReadModel.getAmount())
                        .description(transactionReadModel.getDescription())
                        .transactionDate(transactionReadModel.getTransactionDate().toString())
                        .status(transactionReadModel.getStatus())
                        .failedAt(transactionReadModel.getFailedAt() != null ? transactionReadModel.getFailedAt().toString() : null)
                        .failureReason(transactionReadModel.getFailureReason())
                        .sourceBalanceAfter(transactionReadModel.getSourceBalanceAfter())
                        .destinationBalanceAfter(transactionReadModel.getDestinationBalanceAfter())
                        .createdAt(transactionReadModel.getCreatedAt())
                        .completedAt(transactionReadModel.getCompletedAt())
                        .build();
    }
}
