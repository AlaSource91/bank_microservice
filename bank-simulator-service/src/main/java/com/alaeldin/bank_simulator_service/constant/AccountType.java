package com.alaeldin.bank_simulator_service.constant;

/**
 * Enum representing the type of a bank account.
 */
public enum AccountType {
    /**
     * Personal account for individual customers
     */
    PERSONAL("Personal"),
    /**
     * Business account for corporate customers
     */
    BUSINESS("Business");

    private final String displayName;

    /**
     * Constructor for AccountType enum
     *
     * @param displayName the display name of the account type
     */
    AccountType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name of the account type
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
