package com.alaeldin.bank_query_service.handler;

import com.alaeldin.bank_query_service.model.event.SagaEvent;
import com.alaeldin.bank_query_service.model.readmodel.AccountReadModel;
import com.alaeldin.bank_query_service.model.readmodel.TransactionReadModel;
import com.alaeldin.bank_query_service.repository.AccountReadModelRepository;
import com.alaeldin.bank_query_service.repository.TransactionReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Handles saga lifecycle events from the bank-simulator-service and keeps the
 * CQRS read-model in sync.
 *
 * <p>Each public method corresponds to one {@code eventType} value that arrives
 * on the saga Kafka topics (saga / debit / credit / compensation).</p>
 *
 * <p>Balance mutations use the {@code amount} carried in the event — never a
 * self-subtraction — to avoid producing a zero balance.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SagaEventHandler {

    private final TransactionReadModelRepository transactionRepository;
    private final AccountReadModelRepository accountRepository;

    // -------------------------------------------------------------------------
    // Saga lifecycle
    // -------------------------------------------------------------------------

    /**
     * Creates the initial {@link TransactionReadModel} with status {@code PROCESSING}.
     * Idempotent: skips if the record already exists.
     */
    public void handleSagaStarted(SagaEvent event) {
        log.info("SAGA_STARTED: sagaId={}, txRef={}", event.getEventId(), event.getTransactionReferenceId());

        transactionRepository.findByTransactionId(event.getTransactionReferenceId())
                .ifPresentOrElse(
                        existing -> log.debug("Transaction read-model already exists for txRef={} — skipping create",
                                event.getTransactionReferenceId()),
                        () -> {
                            TransactionReadModel model = TransactionReadModel.builder()
                                    .transactionId(event.getTransactionReferenceId())
                                    .sourceAccountNumber(event.getSourceAccountNumber())
                                    .destinationAccountNumber(event.getDestinationAccountNumber())
                                    .amount(event.getAmount())
                                    .status("PROCESSING")
                                    .transactionDate(event.getTimestamp())
                                    .createdAt(event.getTimestamp())
                                    .build();
                            transactionRepository.save(model);
                            log.info("Transaction read-model created: txRef={}", event.getTransactionReferenceId());
                        });
    }

    /**
     * Marks the transaction as {@code COMPLETED} and records completion time.
     */
    public void handleSagaCompleted(SagaEvent event) {
        log.info("SAGA_COMPLETED: txRef={}", event.getTransactionReferenceId());
        updateTransactionStatus(event.getTransactionReferenceId(), "COMPLETED", null, LocalDateTime.now(), null);
    }

    /**
     * Marks the transaction as {@code FAILED} and records the failure reason.
     */
    public void handleSagaFailed(SagaEvent event) {
        log.warn("SAGA_FAILED: txRef={}, reason={}", event.getTransactionReferenceId(), event.getFailureReason());
        updateTransactionStatus(event.getTransactionReferenceId(), "FAILED",
                event.getFailureReason(), null, LocalDateTime.now());
    }

    /**
     * Marks the transaction as {@code REVERSED} after successful compensation.
     */
    public void handleSagaCompensated(SagaEvent event) {
        log.warn("SAGA_COMPENSATED: txRef={}, reason={}", event.getTransactionReferenceId(), event.getFailureReason());
        updateTransactionStatus(event.getTransactionReferenceId(), "REVERSED",
                event.getFailureReason(), null, LocalDateTime.now());
    }

    // -------------------------------------------------------------------------
    // Debit step
    // -------------------------------------------------------------------------

    /**
     * Subtracts the transferred amount from the source account balance.
     * This is the authoritative balance update — the debit has been confirmed applied.
     */
    public void handleDebitCompleted(SagaEvent event) {
        log.info("DEBIT_COMPLETED: sourceAccount={}, amount={}", event.getSourceAccountNumber(), event.getAmount());
        updateAccountBalance(event.getSourceAccountNumber(), event.getAmount().negate());
    }

    /**
     * Marks the transaction as {@code FAILED} when the debit step cannot proceed.
     * No balance change — the debit was never applied.
     */
    public void handleDebitFailed(SagaEvent event) {
        log.warn("DEBIT_FAILED: txRef={}, reason={}", event.getTransactionReferenceId(), event.getFailureReason());
        updateTransactionStatus(event.getTransactionReferenceId(), "FAILED",
                event.getFailureReason(), null, LocalDateTime.now());
    }

    /**
     * Returns the previously debited amount to the source account (compensation).
     */
    public void handleDebitReversed(SagaEvent event) {
        log.info("DEBIT_REVERSED: sourceAccount={}, amount={}", event.getSourceAccountNumber(), event.getAmount());
        updateAccountBalance(event.getSourceAccountNumber(), event.getAmount());
    }

    // -------------------------------------------------------------------------
    // Credit step
    // -------------------------------------------------------------------------

    /**
     * Adds the transferred amount to the destination account balance.
     */
    public void handleCreditCompleted(SagaEvent event) {
        log.info("CREDIT_COMPLETED: destinationAccount={}, amount={}", event.getDestinationAccountNumber(), event.getAmount());
        updateAccountBalance(event.getDestinationAccountNumber(), event.getAmount());
    }

    /**
     * Marks the transaction as {@code FAILED} when the credit step cannot proceed.
     * A {@code DEBIT_REVERSED} event will follow to compensate.
     */
    public void handleCreditFailed(SagaEvent event) {
        log.warn("CREDIT_FAILED: txRef={}, reason={}", event.getTransactionReferenceId(), event.getFailureReason());
        updateTransactionStatus(event.getTransactionReferenceId(), "FAILED",
                event.getFailureReason(), null, LocalDateTime.now());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Applies a signed {@code delta} to the named account's balance.
     * A positive delta credits; a negative delta debits.
     */
    private void updateAccountBalance(String accountNumber, java.math.BigDecimal delta) {
        Optional<AccountReadModel> optionalAccount = accountRepository.findByAccountNumber(accountNumber);
        if (optionalAccount.isEmpty()) {
            log.warn("Account not found in read-model: {} — balance update skipped", accountNumber);
            return;
        }
        AccountReadModel account = optionalAccount.get();
        java.math.BigDecimal newBalance = account.getBalance().add(delta);
        account.setBalance(newBalance);
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
        log.debug("Balance updated: account={}, delta={}, newBalance={}", accountNumber, delta, newBalance);
    }

    /**
     * Updates the transaction status fields. Pass {@code null} for any field that
     * should not be changed.
     */
    private void updateTransactionStatus(String transactionReferenceId,
                                         String status,
                                         String failureReason,
                                         LocalDateTime completedAt,
                                         LocalDateTime failedAt) {
        transactionRepository.findByTransactionId(transactionReferenceId)
                .ifPresentOrElse(txn -> {
                    txn.setStatus(status);
                    if (failureReason != null) txn.setFailureReason(failureReason);
                    if (completedAt  != null) txn.setCompletedAt(completedAt);
                    if (failedAt     != null) txn.setFailedAt(failedAt);
                    transactionRepository.save(txn);
                    log.debug("Transaction status updated: txRef={}, status={}", transactionReferenceId, status);
                }, () -> log.warn("Transaction not found in read-model: txRef={} — status update skipped",
                        transactionReferenceId));
    }
}
