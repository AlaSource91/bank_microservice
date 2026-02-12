package com.alaeldin.bank_simulator_service.model;


import com.alaeldin.bank_simulator_service.constant.LedgerEntryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_ledger")
@Immutable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionLedger
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 50)
    private String ledgerEntryId;

    @Column(length = 50)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY)
    private BankAccount account;

    @Enumerated(EnumType.STRING)
    private LedgerEntryType entryType;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    private LocalDateTime entryDate;
    private String description;
}


