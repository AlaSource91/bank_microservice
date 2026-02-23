package com.alaeldin.bank_query_service.constant;

import lombok.Getter;

/**
 * Enum representing the type of a bank account.
 * Matches the AccountType from bank-simulator-service for event deserialization.
 */
@Getter
public enum AccountType {
    /**
     * Personal account for individual customers
     */
    PERSONAL("Personal"),

    /**
     * Business account for corporate customers
     */
    BUSINESS("Business");

    /**
     * Human-readable display name for the account type
     */
    private final String displayName;

    /**
     * Constructor for AccountType enum
     * @param displayName the display name for this account type
     */
    AccountType(String displayName) {
        this.displayName = displayName;
    }
}

