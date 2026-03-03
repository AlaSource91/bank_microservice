
package com.alaeldin.bank_simulator_service.dto;

import com.alaeldin.bank_simulator_service.constant.IdempotencyStatus;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TransactionIdempotencyDto
{

    private String idempotencyKey;
    private BankTransaction transaction;
    private String requestHash;
    private IdempotencyStatus status;
    private String responseData;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;

}
