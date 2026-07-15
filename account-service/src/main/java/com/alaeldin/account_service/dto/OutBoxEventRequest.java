package com.alaeldin.account_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data

public class OutBoxEventRequest {

   String aggregateId;
   String aggregateType;
   String eventType;
   Object eventPayload;
   String idempotencyKey;

}
