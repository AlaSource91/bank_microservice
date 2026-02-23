package com.alaeldin.bank_query_service.constant;

public enum EventType {

    TRANSACTION_COMPLETED("transaction_completed"),
    TRANSACTION_FAILED("transaction_failed"),
    TRANSACTION_STARTED("transaction.started"),
    ACCOUNT_BALANCE_UPDATED("account.balance.updated"),
    LEDGER_ENTRY_CREATED("ledger.entry.created");

    private final String eventName;

    EventType(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }
}
