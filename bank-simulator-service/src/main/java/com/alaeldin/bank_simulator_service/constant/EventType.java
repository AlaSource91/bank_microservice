package com.alaeldin.bank_simulator_service.constant;

public enum EventType
{
    // Define event types with their corresponding event names
    TRANSACTION_COMPLETED("transaction_completed"),
    TRANSACTION_FAILED("transaction_failed"),
    TRANSACTION_STARTED("transaction.started"),
    ACCOUNT_BALANCE_UPDATED("account.balance.updated"),
    LEDGER_ENTRY_CREATED("ledger.entry.created");

    // Each enum constant has an associated event name
    private final String eventName;

    EventType(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }
}
