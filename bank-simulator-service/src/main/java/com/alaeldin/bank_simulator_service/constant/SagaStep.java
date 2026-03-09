package com.alaeldin.bank_simulator_service.constant;

import lombok.Getter;

@Getter
public enum SagaStep {

    INIT("Initialize"),
    DEBIT_SOURCE("Debit Source Account"),
    CREDIT_DESTINATION("Credit Destination Account"),
    COMPENSATE_DEBIT("Compensate Debit"),
    COMPENSATE_CREDIT("Compensate Credit"),
    COMPLETE("Complete Transaction"),
    SAGA_FAILED("Saga Failed");

    private final String displayName;

    SagaStep(String displayName) {
        this.displayName = displayName;
    }

}
