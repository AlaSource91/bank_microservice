package com.alaeldin.read_account_service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseEvent {

    private String id;
    private String eventId;
    private String accountNumber;
    private String eventType;
    private String aggregateType; //LEDGER // ACCOUNT // LEDGER

}
