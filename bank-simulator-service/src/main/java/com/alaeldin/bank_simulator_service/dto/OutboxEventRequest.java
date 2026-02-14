package com.alaeldin.bank_simulator_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventRequest
{
    String aggregateId;
    String aggregateType;
    String eventType;
    Object eventPayload; // JSON Payload
    String idempotencyKey; // Unique key to prevent duplicates
}
