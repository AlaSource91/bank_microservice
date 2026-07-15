package com.alaeldin.bank_query_service.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseEvent
{
    private String id;
    private String eventId;
    private String accountNumber;
    private String eventType;
    private String aggregateType; // ACCOUNT / TRANSACTION / LEDGER

}
