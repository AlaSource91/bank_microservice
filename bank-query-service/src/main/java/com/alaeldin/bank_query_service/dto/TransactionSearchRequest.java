package com.alaeldin.bank_query_service.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class TransactionSearchRequest {

    @PastOrPresent(message = "Start date must be in the past or present")
    private LocalDateTime startDate;

    @PastOrPresent(message = "End date must be in the past or present")
    private LocalDateTime endDate;
}
