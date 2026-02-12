package com.alaeldin.bank_simulator_service.constant;

/**
 * Enum representing the status of a bank transaction.
 */
public enum StatusTransaction {
    /**
     * Transaction is pending and awaiting processing
     */
    PENDING("Pending"),
    /**
     * Transaction is currently being processed
     */
    PROCESSING("Processing"),
    /**
     * Transaction has been completed successfully
     */
    COMPLETED("Completed"),
    /**
     * Transaction has failed
     */
    FAILED("Failed"),
    /**
     * Transaction has been reversed
     */
    REVERSED("Reversed"),
    /**
     * Transaction has timed out
     */
    TIMED_OUT("Timed Out");

    private final String displayName;

    /**
     * Constructor for StatusTransaction enum
     *
     * @param displayName the display name of the transaction status
     */
    StatusTransaction(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name of the transaction status
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
