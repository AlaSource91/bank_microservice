package com.alaeldin.bank_query_service.model.event;

import lombok.Data;

@Data
public abstract class BaseEvent
{
    private String id;
    private String eventId;
    private String accountNumber;
    private String eventType;
    private String aggregateType; // ACCOUNT / TRANSACTION / LEDGER

}
