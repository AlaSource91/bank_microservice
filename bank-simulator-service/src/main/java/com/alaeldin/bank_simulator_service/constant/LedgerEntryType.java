package com.alaeldin.bank_simulator_service.constant;

public enum LedgerEntryType
{
    DEBIT("De"), CREDIT("Cr");
    private String code;
    LedgerEntryType(String code)
    {
        this.code = code;
    }

    public String getCode()
    {
        return code;
    }
}

