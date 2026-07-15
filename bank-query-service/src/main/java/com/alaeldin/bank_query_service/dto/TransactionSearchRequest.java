package com.alaeldin.bank_query_service.dto;

import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class TransactionSearchRequest {

    private LocalDate startDate;

    private LocalDate endDate;
}
