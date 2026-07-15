package com.alaeldin.account_service.constant;

import lombok.Getter;

@Getter
public enum AccountType {
    PERSONAL("Personal"),
    BUSINESS("Business");

    private final String displayName;
    AccountType(String displayName) {
        this.displayName = displayName;
    }
}
