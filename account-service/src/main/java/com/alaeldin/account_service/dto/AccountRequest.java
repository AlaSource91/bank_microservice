package com.alaeldin.account_service.dto;

import com.alaeldin.account_service.constant.AccountType;
import lombok.*;
import jakarta.validation.constraints.NotNull;
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class AccountRequest {

    @NotNull(message = "Account type cannot be null")
    private AccountType accountType;

}
