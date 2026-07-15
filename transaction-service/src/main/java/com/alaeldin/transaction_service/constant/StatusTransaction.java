package com.alaeldin.transaction_service.constant;

public enum StatusTransaction {

    /**
     * Transaction Is Pending and awaiting Processing
     */
    PENDING("Pending"),
    /**
     * Transaction is Currently being processed
     */
    PROCESSING("Processing"),
    /**
     * Transaction has been completed Successfully
     */
    COMPLETED("Completed"),
    /**
     * Transaction Has Failed
     */
    FAILED("Failed"),
    /**
     * Transaction has been reversed
     */
    REVERSED("Reversed") ,
    /**
     * Transaction has Timed out
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
