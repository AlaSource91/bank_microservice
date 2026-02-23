package com.alaeldin.bank_query_service.dto;

import com.alaeldin.bank_query_service.constant.AccountStatus;
import com.alaeldin.bank_query_service.model.readmodel.AccountReadModel;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for account query responses.
 * Used in CQRS read-side to return account information to clients.
 * This DTO represents the read model projection of account data.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountQueryResponse {

    /**
     * The unique account number.
     */
    private String accountNumber;

    /**
     * The name of the account holder.
     */
    private String accountHolderName;

    /**
     * The type of account (e.g., PERSONAL, BUSINESS).
     */
    private String accountType;

    /**
     * The current balance of the account.
     */
    private BigDecimal balance;

    /**
     * The current status of the account (e.g., ACTIVE, FROZEN, CLOSED).
     */
    private AccountStatus status;

    /**
     * The date when the account was created.
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdDate;

    /**
     * The date when the account was last updated.
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedDate;

    /**
     * Application version that created/updated this account data.
     */
    private String version;

    /**
     * Name of the application that manages this account.
     */
    private String applicationName;

    /**
     * Factory method to create AccountQueryResponse from AccountReadModel.
     * Converts the read model entity to a DTO for API responses.
     *
     * @param accountReadModel the account read model from MongoDB
     * @return AccountQueryResponse DTO
     */
    public static AccountQueryResponse fromReadModel(AccountReadModel accountReadModel) {
        if (accountReadModel == null) {
            return null;
        }

        return AccountQueryResponse.builder()
                .accountNumber(accountReadModel.getAccountNumber())
                .accountHolderName(accountReadModel.getAccountHolderName())
                .accountType(accountReadModel.getAccountType())
                .balance(accountReadModel.getBalance())
                .status(accountReadModel.getStatus())
                .createdDate(accountReadModel.getCreatedAt())
                .updatedDate(accountReadModel.getUpdatedAt())
                .version(accountReadModel.getVersion())
                .applicationName(accountReadModel.getApplicationName())
                .build();
    }
}
