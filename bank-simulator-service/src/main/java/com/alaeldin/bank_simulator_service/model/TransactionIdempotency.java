package com.alaeldin.bank_simulator_service.model;

import com.alaeldin.bank_simulator_service.constant.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_idempotency")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionIdempotency {

    @Id
    @Column(length = 255)
    private String idempotencyKey;
    @ManyToOne()
    @JoinColumn(name ="transaction_id")
    private BankTransaction transaction;
    @Column(length = 255)
    private String requestHash;
    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;
    @Column(columnDefinition = "JSON")
    private String responseData;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;

   public boolean isExpired() {
        return expiresAt != null
                && LocalDateTime.now().isAfter(expiresAt);
    }
}
