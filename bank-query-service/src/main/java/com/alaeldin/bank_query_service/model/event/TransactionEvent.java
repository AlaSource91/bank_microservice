package com.alaeldin.bank_query_service.model.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionEvent extends BaseEvent {

    private String transactionId;
    private String statusTransaction;
    private String sourceAccountHolderName;
    private String destinationAccountHolderName;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private BigDecimal sourceBalanceAfter;
    private BigDecimal destinationBalanceAfter;
    private String description;
    private String failureReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime failedAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS][.SSSSSSSS][.SSSSSSS][.SSSSSS][.SSSSS][.SSSS][.SSS][.SS][.S]")
    private LocalDateTime transactionDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
    private String applicationName;
    private String version;

}
