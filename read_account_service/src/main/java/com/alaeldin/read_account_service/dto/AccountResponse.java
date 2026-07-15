package com.alaeldin.read_account_service.dto;

import com.alaeldin.read_account_service.constant.AccountStatus;
import com.alaeldin.read_account_service.model.Account;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse {

    /**
     * The unique Account Number.
     */
    private String accountNumber;
    /**
     * The Name Of The Account Holder.
     */
    private String accountHolderName;
    /**
     * The Account Type Such As (e.g.PERSONAL, BUSINESS).
     */
    private String accountType;
    /**
     * The Current Balance of Account.
     */
    private BigDecimal balance;
    /**
     * The current status of the account (e.g., ACTIVE, FROZEN, CLOSED).
     */
    private AccountStatus status;
    /**
     * The Data When the Account was Created.
     */
    @JsonFormat(pattern = "YYY-MM-dd  HH:mm:ss")
    private LocalDateTime createdAt;
    /**
     * The date when the account was last updated.
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    /**
     * Application version that created/updated this account data.
     */
    private String version;
    /**
     * Name of the application that manages this account.
     */
    private String applicationName;

    /**
     * Factory Method to create AccountQueryResponse from AccountReadModel.
     *
     * @Param account the account read model from Mongo DB
     * @return Account QueryResponse DTO
      */
    public static AccountResponse from(Account account) {
        if (account == null) {
            return null;
        }

        return AccountResponse
                .builder()
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .status(account.getAccountStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .version(account.getVersion())
                .applicationName(account.getApplicationName())
                .build();

    }
}
