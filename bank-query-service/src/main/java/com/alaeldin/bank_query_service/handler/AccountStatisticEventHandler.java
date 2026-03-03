package com.alaeldin.bank_query_service.handler;

import com.alaeldin.bank_query_service.model.event.TransactionEvent;
import com.alaeldin.bank_query_service.model.readmodel.AccountStatisticsReadModel;
import com.alaeldin.bank_query_service.repository.AccountStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles account statistics updates triggered by completed transaction events.
 * <p>
 * Maintains a daily {@link AccountStatisticsReadModel} per account using upsert semantics:
 * <ul>
 *   <li>The <em>source</em> account of a transaction always receives a DEBIT entry.</li>
 *   <li>The <em>destination</em> account always receives a CREDIT entry.</li>
 * </ul>
 * Called by the Kafka consumer after a {@code TRANSACTION_COMPLETED} event is received.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountStatisticEventHandler {

    private final AccountStatisticsRepository accountStatisticsRepository;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Upserts daily statistics for the <strong>source</strong> account (DEBIT side).
     *
     * @param event the completed transaction event
     */
    public void handleDebitSide(TransactionEvent event) {
        String accountNumber = event.getSourceAccount();
        log.info("Updating DEBIT statistics for accountNumber={}", accountNumber);
        // Opening balance = balance after the debit + amount (i.e. balance before the transaction)
        BigDecimal openingBalance = event.getSourceBalanceAfter() != null
                ? event.getSourceBalanceAfter().add(event.getAmount())
                : BigDecimal.ZERO;
        applyStatistics(accountNumber, event.getAmount(), true,
                openingBalance, event.getSourceBalanceAfter(), event.getCompletedAt());
    }

    /**
     * Upserts daily statistics for the <strong>destination</strong> account (CREDIT side).
     *
     * @param event the completed transaction event
     */
    public void handleCreditSide(TransactionEvent event) {
        String accountNumber = event.getDestinationAccount();
        log.info("Updating CREDIT statistics for accountNumber={}", accountNumber);
        // Opening balance = balance after the credit - amount (i.e. balance before the transaction)
        BigDecimal openingBalance = event.getDestinationBalanceAfter() != null
                ? event.getDestinationBalanceAfter().subtract(event.getAmount())
                : BigDecimal.ZERO;
        applyStatistics(accountNumber, event.getAmount(), false,
                openingBalance, event.getDestinationBalanceAfter(), event.getCompletedAt());
    }

    /**
     * Convenience method that updates statistics for both sides of a transaction.
     *
     * @param event the completed transaction event
     */
    public void handleTransactionCompleted(TransactionEvent event) {
        handleDebitSide(event);
        handleCreditSide(event);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Core upsert logic for a single account / date combination.
     *
     * @param accountNumber  the account to update
     * @param amount         the transaction amount (always positive)
     * @param isDebit        {@code true} for the source/debit side
     * @param openingBalance balance before this transaction (derived from event)
     * @param closingBalance balance after this transaction
     * @param completedAt    timestamp of the transaction; falls back to {@link LocalDate#now()} if {@code null}
     */
    private void applyStatistics(String accountNumber,
                                 BigDecimal amount,
                                 boolean isDebit,
                                 BigDecimal openingBalance,
                                 BigDecimal closingBalance,
                                 LocalDateTime completedAt) {
        try {
            LocalDate date = completedAt != null
                    ? completedAt.toLocalDate()
                    : LocalDate.now();

            AccountStatisticsReadModel stats = findOrCreate(accountNumber, date, openingBalance, closingBalance);

            incrementCounters(stats, amount, isDebit);
            stats.setClosingBalance(closingBalance);
            stats.setCalculatedAt(LocalDateTime.now());

            accountStatisticsRepository.save(stats);
            log.info("Statistics upserted – accountNumber={}, date={}, isDebit={}", accountNumber, date, isDebit);

        } catch (Exception e) {
            log.error("Failed to update statistics for accountNumber={}: {}", accountNumber, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Fetches an existing daily statistics record or creates a zero-initialised one.
     * <p>
     * When creating a new record, {@code openingBalance} is the balance <em>before</em>
     * the first transaction of the day, and {@code closingBalance} is the balance
     * <em>after</em> it — both derived from the event, so no extra database round-trip
     * is required.
     */
    private AccountStatisticsReadModel findOrCreate(String accountNumber,
                                                    LocalDate date,
                                                    BigDecimal openingBalance,
                                                    BigDecimal closingBalance) {
        return accountStatisticsRepository
                .findByAccountNumberAndDate(accountNumber, date)
                .orElseGet(() -> AccountStatisticsReadModel.builder()
                        .id(UUID.randomUUID().toString())
                        .accountNumber(accountNumber)
                        .date(date)
                        .transactionCount(0L)
                        .totalDebit(BigDecimal.ZERO)
                        .totalCredit(BigDecimal.ZERO)
                        .netFlow(BigDecimal.ZERO)
                        .openingBalance(openingBalance)   // balance BEFORE the transaction
                        .closingBalance(closingBalance)   // balance AFTER the transaction
                        .calculatedAt(LocalDateTime.now())
                        .build());
    }

    /**
     * Increments transaction counters and recalculates net flow.
     * Net flow = total credits − total debits (positive value = net inflow).
     */
    private void incrementCounters(AccountStatisticsReadModel stats,
                                   BigDecimal amount,
                                   boolean isDebit) {
        stats.setTransactionCount(stats.getTransactionCount() + 1);

        if (isDebit) {
            stats.setTotalDebit(stats.getTotalDebit().add(amount));
        } else {
            stats.setTotalCredit(stats.getTotalCredit().add(amount));
        }

        stats.setNetFlow(stats.getTotalCredit().subtract(stats.getTotalDebit()));
    }
}
