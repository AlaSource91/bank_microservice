package com.alaeldin.bank_simulator_service.constant;

import lombok.Getter;

@Getter
public enum EventType
{
    // Define event types with their corresponding event names
    TRANSACTION_COMPLETED("transaction_completed"),
    TRANSACTION_FAILED("transaction_failed"),
    TRANSACTION_STARTED("transaction.started"),
    ACCOUNT_BALANCE_UPDATED("account.balance.updated"),
    LEDGER_ENTRY_CREATED("ledger.entry.created"),
    //value  New Saga event types
    SAGA_STARTED("saga.started"),
    SAGA_COMPLETED("saga.completed"),
    SAGA_COMPENSATED("saga.compensated"),
        SAGA_FAILED("saga.failed"),
    DEBIT_REQUESTED("debit.requested"),
    CREDIT_REQUESTED("credit.requested"),
    DEBIT_COMPLETED("debit.completed"),
    CREDIT_COMPLETED("credit.completed"),
    DEBIT_FAILED("debit.failed"),
    DEBIT_REVERSED("debit.reversed"),
    CREDIT_FAILED("credit.failed");




    // Each enum constant has an associated event name
    private final String eventName;

    EventType(String eventName) {
        this.eventName = eventName;
    }

}
