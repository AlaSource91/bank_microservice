package com.alaeldin.bank_query_service.constant;

import lombok.Getter;

@Getter
public enum AccountStatus
{
    ACTIVE("Active"),
    FROZEN("Frozen"),
    CLOSED("Closed");

    private final String displayName;

    AccountStatus(String displayName) {
        this.displayName = displayName;
    }
}
