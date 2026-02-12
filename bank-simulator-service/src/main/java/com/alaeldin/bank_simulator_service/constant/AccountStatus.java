package com.alaeldin.bank_simulator_service.constant;

/**
 * Enum representing the status of a bank account.
 */
public enum AccountStatus {
    /**
     * Account is active and can be used for transactions
     */
    ACTIVE("Active"),
    /**
     * Account is frozen and cannot be used for transactions
     */
    FROZEN("Frozen"),
    /**
     * Account is closed and no longer in use
     */
    CLOSED("Closed");

    private final String displayName;

    /**
     * Constructor for AccountStatus enum
     *
     * @param displayName the display name of the account status
     */
    AccountStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name of the account status
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
