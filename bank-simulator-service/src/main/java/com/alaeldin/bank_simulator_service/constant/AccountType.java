package com.alaeldin.bank_simulator_service.constant;

import lombok.Getter;

/**
 * Enum representing the type of a bank account.
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
     * -- GETTER --
     *  Gets the display name of the account type
     *
     * @return the display name
     */
    private final String displayName;

    /**
     * Constructor for AccountType enum
     *
     * @param displayName the display name of the account type
     */
    AccountType(String displayName) {
        this.displayName = displayName;
    }

}
