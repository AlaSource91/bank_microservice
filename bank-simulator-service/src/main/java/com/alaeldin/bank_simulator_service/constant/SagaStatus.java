package com.alaeldin.bank_simulator_service.constant;

import lombok.Getter;

@Getter
public enum SagaStatus {

    PENDING("Pending"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    COMPENSATING("Compensating"),
    COMPENSATED("Compensated"),
    DEBIT_COMPLETED("Debit Completed"),
    CREDIT_COMPLETED("Credit Completed");

    private final String displayName;
    SagaStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean isFinalResult() {
        switch (this) {
            case COMPLETED, FAILED, COMPENSATED -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

}
