package com.alaeldin.bank_query_service.constant;

/**
 * Enum representing different types of account events in the CQRS system.
 * These event types correspond to the events published by the bank-simulator-service.
 */
public enum AccountEventType {
    /**
     * Event triggered when a new bank account is created
     */
    ACCOUNT_CREATED,

    /**
     * Event triggered when a bank account is frozen or suspended
     */
    ACCOUNT_FROZEN,

    /**
     * Event triggered when account details are updated
     */
    ACCOUNT_UPDATED,

    /**
     * Event triggered when account balance is updated
     */
    ACCOUNT_BALANCE_UPDATED
}
