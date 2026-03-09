package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.EventType;
import com.alaeldin.bank_simulator_service.constant.SagaStatus;
import com.alaeldin.bank_simulator_service.constant.SagaStep;
import com.alaeldin.bank_simulator_service.constant.StatusTransaction;
import com.alaeldin.bank_simulator_service.exception.AccountLockedException;
import com.alaeldin.bank_simulator_service.model.BankTransaction;
import com.alaeldin.bank_simulator_service.model.SagaState;
import com.alaeldin.bank_simulator_service.repository.BankTransactionRepository;
import com.alaeldin.bank_simulator_service.repository.SagaStateRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * Orchestrates the P2P transfer Saga using the choreography-based Outbox pattern.
 *
 * <p>The saga follows a strict step sequence:</p>
 * <pre>
 *   INIT → DEBIT_SOURCE → CREDIT_DESTINATION → COMPLETE
 *                 ↓ (on failure)
 *           COMPENSATE_DEBIT → COMPENSATED / FAILED
 * </pre>
 *
 * <p>Every state transition is persisted atomically before the corresponding
 * outbox event is published, guaranteeing that the downstream query-service
 * always sees a consistent view.</p>
 *
 * <p><b>Transaction boundary:</b> All methods participate in the transaction
 * opened by the public entry-point ({@link #startSaga}).  Private helper methods
 * intentionally do NOT declare {@code @Transactional} — Spring AOP proxies cannot
 * intercept private calls, so the annotation would be silently ignored.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SagaOrchestrationService {

    private final SagaStateRepository sagaStateRepository;
    private final AccountVersionService accountVersionService;
    private final BankTransactionRepository bankTransactionRepository;
    private final LedgerService ledgerService;
    private final SagaEventPublisher sagaEventPublisher;
    private final EventPublishingService eventPublishingService;

    /** Maximum number of automatic retries for a single step. */
    private static final int DEFAULT_MAX_RETRIES = 3;

    // =========================================================================
    // Public entry-point
    // =========================================================================

    /**
     * Starts a new saga for the given bank transaction.
     *
     * <p>Creates the initial {@link SagaState} record, publishes a
     * {@code SAGA_STARTED} event, and immediately drives the saga into the
     * debit step — all within a single database transaction.</p>
     *
     * @param transaction the PENDING transaction to process; must not be {@code null}
     * @return the final {@link SagaState} after all synchronous steps complete
     * @throws IllegalStateException    if a saga already exists for this transaction reference
     * @throws IllegalArgumentException if {@code transaction} is {@code null}
     */
    @Transactional
    public SagaState startSaga(BankTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction must not be null");
        }

        log.info("Starting saga for transactionId={}, referenceId={}",
                transaction.getId(), transaction.getReferenceId());

        // Guard: prevent duplicate sagas for the same transaction reference
        sagaStateRepository
                .findByTransactionReferenceId(transaction.getReferenceId())
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Saga already exists for transactionReferenceId=" + transaction.getReferenceId());
                });

        SagaState saga = SagaState.builder()
                .sagaId("SAGA-" + transaction.getReferenceId())
                .transactionReferenceId(transaction.getReferenceId())
                .status(SagaStatus.PENDING)
                .currentStep(SagaStep.INIT)
                .sourceAccountNumber(transaction.getSourceAccount().getAccountNumber())
                .destinationAccountNumber(transaction.getDestinationAccount().getAccountNumber())
                .amount(transaction.getAmount())
                .maxRetries(DEFAULT_MAX_RETRIES)
                .startedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        saga = sagaStateRepository.save(saga);
        sagaEventPublisher.publishWithOutBox(saga, EventType.SAGA_STARTED);

        return executeDebitStep(saga.getSagaId());
    }

    // =========================================================================
    // Step: Debit source account
    // =========================================================================

    /**
     * Executes the debit step for the saga identified by {@code sagaId}.
     *
     * <p>If the debit was already completed (idempotent re-entry), the method
     * skips straight to the credit step. On retryable failures (optimistic lock,
     * account locked) the retry counter is incremented; once the budget is
     * exhausted the saga is failed.</p>
     *
     * @param sagaId the saga identifier
     * @return the updated {@link SagaState}
     */
    private SagaState executeDebitStep(String sagaId) {
        SagaState saga = getSagaOrThrow(sagaId);

        // Idempotency: debit already done — skip ahead
        if (saga.getStatus() == SagaStatus.DEBIT_COMPLETED) {
            log.info("Debit already completed for sagaId={} — proceeding to credit step", sagaId);
            return executeCreditStep(sagaId);
        }

        log.info("Executing debit step: sagaId={}, account={}, amount={}",
                sagaId, saga.getSourceAccountNumber(), saga.getAmount());
        sagaEventPublisher.publishWithOutBox(saga, EventType.DEBIT_REQUESTED);

        try {
            accountVersionService.updateBalanceWithVersionCheck(
                    saga.getSourceAccountNumber(),
                    saga.getAmount().negate(),
                    sagaId
            );

            //  FIX: was incorrectly set to COMPLETED — debit step is DEBIT_COMPLETED
            saga.setStatus(SagaStatus.DEBIT_COMPLETED);
            saga.setCurrentStep(SagaStep.DEBIT_SOURCE);
            saga.setUpdatedAt(LocalDateTime.now());
            saga = sagaStateRepository.save(saga);
            sagaEventPublisher.publishWithOutBox(saga, EventType.DEBIT_COMPLETED);

            return executeCreditStep(sagaId);

        } catch (AccountLockedException | OptimisticLockingFailureException |
                 OptimisticLockException | IllegalArgumentException e) {

            sagaEventPublisher.publishWithOutBox(saga, EventType.DEBIT_FAILED);
            log.warn("Debit step failed for sagaId={}: {}", sagaId, e.getMessage());

            if (saga.canRetry()) {
                saga.setRetryCount(saga.getRetryCount() + 1);
                saga.setUpdatedAt(LocalDateTime.now());
                sagaStateRepository.save(saga);
                log.info("Debit retry scheduled: sagaId={}, attempt={}/{}",
                        sagaId, saga.getRetryCount(), saga.getMaxRetries());
                return saga;
            }

            // Retry budget exhausted — fail the saga
            return handleSagaFailure(sagaId, "Debit step failed after max retries: " + e.getMessage());

        } catch (Exception e) {
            sagaEventPublisher.publishWithOutBox(saga, EventType.DEBIT_FAILED);
            log.error("Unexpected error during debit step for sagaId={}: {}", sagaId, e.getMessage(), e);
            return handleSagaFailure(sagaId, "Unexpected error during debit step: " + e.getMessage());
        }
    }

    // =========================================================================
    // Step: Credit destination account
    // =========================================================================

    /**
     * Executes the credit step for the saga identified by {@code sagaId}.
     *
     * <p>Requires the saga to be in {@link SagaStatus#DEBIT_COMPLETED} state.
     * On failure the saga is handed off to {@link #handleSagaFailure}, which
     * triggers debit compensation because money has already left the source account.</p>
     *
     * @param sagaId the saga identifier
     * @return the updated {@link SagaState}
     */
    public  SagaState executeCreditStep(String sagaId) {
        SagaState saga = getSagaOrThrow(sagaId);

        // Idempotency: credit already done — skip ahead
        if (saga.getStatus() == SagaStatus.CREDIT_COMPLETED) {
            log.info("Credit already completed for sagaId={} — proceeding to complete", sagaId);
            return completeSaga(sagaId);
        }

        if (saga.getStatus() != SagaStatus.DEBIT_COMPLETED) {
            throw new IllegalStateException(
                    "Cannot execute credit step: expected DEBIT_COMPLETED, got " + saga.getStatus());
        }

        log.info("Executing credit step: sagaId={}, account={}, amount={}",
                sagaId, saga.getDestinationAccountNumber(), saga.getAmount());
        sagaEventPublisher.publishWithOutBox(saga, EventType.CREDIT_REQUESTED);

        try {
            accountVersionService.updateBalanceWithVersionCheck(
                    saga.getDestinationAccountNumber(),
                    saga.getAmount(),
                    sagaId
            );

            saga.setStatus(SagaStatus.CREDIT_COMPLETED);
            saga.setCurrentStep(SagaStep.CREDIT_DESTINATION);
            saga.setUpdatedAt(LocalDateTime.now());
            saga = sagaStateRepository.save(saga);
            sagaEventPublisher.publishWithOutBox(saga, EventType.CREDIT_COMPLETED);

            return completeSaga(sagaId);

        } catch (AccountLockedException | OptimisticLockingFailureException |
                 OptimisticLockException | IllegalArgumentException e) {

            sagaEventPublisher.publishWithOutBox(saga, EventType.CREDIT_FAILED);
            log.warn("Credit step failed for sagaId={}: {}", sagaId, e.getMessage());
            return handleSagaFailure(sagaId, "Credit step failed: " + e.getMessage());

        } catch (Exception e) {
            sagaEventPublisher.publishWithOutBox(saga, EventType.CREDIT_FAILED);
            log.error("Unexpected error during credit step for sagaId={}: {}", sagaId, e.getMessage(), e);
            return handleSagaFailure(sagaId, "Unexpected error during credit step: " + e.getMessage());
        }
    }

    // =========================================================================
    // Step: Complete saga
    // =========================================================================

    /**
     * Marks the saga and its underlying transaction as {@code COMPLETED}, creates
     * the double-entry ledger records, and publishes the final events.
     *
     * <p>Idempotent — returns immediately if the saga is already completed.</p>
     *
     * @param sagaId the saga identifier
     * @return the completed {@link SagaState}
     */
    private SagaState completeSaga(String sagaId) {
        SagaState saga = getSagaOrThrow(sagaId);

        if (saga.isCompleted()) {
            log.info("Saga already completed: sagaId={}", sagaId);
            return saga;
        }

        BankTransaction txn = findTransactionOrThrow(saga.getTransactionReferenceId());

        txn.setStatus(StatusTransaction.COMPLETED);
        txn.setCompletedAt(LocalDateTime.now());
        txn.setUpdatedAt(LocalDateTime.now());
        bankTransactionRepository.save(txn);

        ledgerService.createLedgerEntries(txn);

        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCurrentStep(SagaStep.COMPLETE);
        saga.setCompletedAt(LocalDateTime.now());
        saga.setUpdatedAt(LocalDateTime.now());
        saga = sagaStateRepository.save(saga);

        sagaEventPublisher.publishWithOutBox(saga, EventType.SAGA_COMPLETED);
        eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_COMPLETED);

        log.info("Saga completed successfully: sagaId={}, referenceId={}",
                sagaId, saga.getTransactionReferenceId());
        return saga;
    }

    // =========================================================================
    // Failure & compensation
    // =========================================================================

    /**
     * Handles a saga failure by either triggering compensation (when the debit
     * already succeeded) or directly marking the saga as {@code FAILED}.
     *
     * <p><b>Compensation guard:</b> compensation is required only when the saga
     * reached {@link SagaStatus#DEBIT_COMPLETED} — i.e. money has already left
     * the source account and must be returned.</p>
     *
     * @param sagaId the saga identifier
     * @param reason human-readable failure reason (stored on the saga and transaction)
     * @return the updated {@link SagaState}
     */
    private SagaState handleSagaFailure(String sagaId, String reason) {
        SagaState saga = getSagaOrThrow(sagaId);

        //  FIX: compensation is needed when the debit already succeeded
        boolean debitAlreadyApplied = saga.getStatus() == SagaStatus.DEBIT_COMPLETED
                || saga.getStatus() == SagaStatus.CREDIT_COMPLETED;

        if (debitAlreadyApplied) {
            log.warn("Debit was applied — initiating compensation: sagaId={}, reason={}", sagaId, reason);
            saga.setStatus(SagaStatus.COMPENSATING);
            saga.setUpdatedAt(LocalDateTime.now());
            saga = sagaStateRepository.save(saga);
            return compensateDebit(saga.getSagaId(), reason);
        }

        // No money moved yet — mark as failed directly
        saga.setStatus(SagaStatus.FAILED);
        saga.setCurrentStep(SagaStep.SAGA_FAILED);
        saga.setFailureReason(reason);
        saga.setCompletedAt(LocalDateTime.now());
        saga.setUpdatedAt(LocalDateTime.now());
        sagaStateRepository.save(saga);

        markTransactionFailed(saga.getTransactionReferenceId(), reason);
        sagaEventPublisher.publishWithOutBox(saga, EventType.SAGA_FAILED);

        log.error("Saga failed: sagaId={}, reason={}", sagaId, reason);
        return saga;
    }

    /**
     * Reverses a previously applied debit by crediting the source account back.
     *
     * <p>The saga must be in {@link SagaStatus#COMPENSATING} state when this method
     * is called. If the compensation itself fails, the saga is marked {@code FAILED}
     * and flagged for manual intervention.</p>
     *
     * @param sagaId the saga identifier
     * @param reason the reason triggering compensation (stored for audit)
     * @return the updated {@link SagaState}
     */
    public SagaState compensateDebit(String sagaId, String reason) {
        SagaState saga = getSagaOrThrow(sagaId);

        if (saga.getStatus() != SagaStatus.COMPENSATING) {
            throw new IllegalStateException(
                    "Saga must be in COMPENSATING state for debit reversal, current: " + saga.getStatus());
        }

        log.info("Compensating debit: sagaId={}, account={}, amount={}, reason={}",
                sagaId, saga.getSourceAccountNumber(), saga.getAmount(), reason);

        try {
            accountVersionService.updateBalanceWithVersionCheck(
                    saga.getSourceAccountNumber(),
                    saga.getAmount(),                       // positive — returning the money
                    sagaId + "_COMPENSATION"
            );

            BankTransaction txn = findTransactionOrThrow(saga.getTransactionReferenceId());
            txn.setStatus(StatusTransaction.REVERSED);
            txn.setFailedAt(LocalDateTime.now());
            txn.setErrorMessage(reason);
            txn.setUpdatedAt(LocalDateTime.now());
            bankTransactionRepository.save(txn);

            saga.setStatus(SagaStatus.COMPENSATED);
            saga.setCurrentStep(SagaStep.COMPENSATE_DEBIT);
            saga.setFailureReason(reason);
            saga.setCompletedAt(LocalDateTime.now());
            saga.setUpdatedAt(LocalDateTime.now());
            saga = sagaStateRepository.save(saga);

            sagaEventPublisher.publishWithOutBox(saga, EventType.DEBIT_REVERSED);
            eventPublishingService.publishEventWithOutboxSupport(txn, EventType.TRANSACTION_FAILED);
            sagaEventPublisher.publishWithOutBox(saga, EventType.SAGA_COMPENSATED);

            log.info("Saga compensated successfully: sagaId={}, reason={}", sagaId, reason);
            return saga;

        } catch (Exception e) {
            log.error("CRITICAL: Compensation failed for sagaId={} — manual intervention required: {}",
                    sagaId, e.getMessage(), e);

            saga.setStatus(SagaStatus.FAILED);
            saga.setCurrentStep(SagaStep.SAGA_FAILED);
            saga.setFailureReason("Compensation failed: " + e.getMessage());
            saga.setCompletedAt(LocalDateTime.now());
            saga.setUpdatedAt(LocalDateTime.now());
            sagaStateRepository.save(saga);

            sagaEventPublisher.publishWithOutBox(saga, EventType.SAGA_FAILED);
            return saga;
        }
    }

    // =========================================================================
    // Private utilities
    // =========================================================================

    /**
     * Updates the underlying {@link BankTransaction} to {@code FAILED} status and
     * publishes the corresponding event. Logs a warning if the transaction cannot
     * be found (should not happen in normal operation).
     */
    private void markTransactionFailed(String transactionReferenceId, String reason) {
        bankTransactionRepository.findByReferenceId(transactionReferenceId)
                .ifPresentOrElse(
                        txn -> {
                            txn.setStatus(StatusTransaction.FAILED);
                            txn.setFailedAt(LocalDateTime.now());
                            txn.setErrorMessage(reason);
                            txn.setUpdatedAt(LocalDateTime.now());
                            bankTransactionRepository.save(txn);
                            eventPublishingService.publishEventWithOutboxSupport(
                                    txn, EventType.TRANSACTION_FAILED);
                            log.info("Transaction marked as FAILED: referenceId={}, reason={}",
                                    transactionReferenceId, reason);
                        },
                        () -> log.warn("Transaction not found when marking as failed: referenceId={}",
                                transactionReferenceId)
                );
    }

    /**
     * Loads a {@link SagaState} by ID or throws {@link IllegalStateException}.
     *
     * @param sagaId the saga ID to look up
     * @return the loaded saga state — never {@code null}
     * @throws IllegalStateException if no saga exists with the given ID
     */
    private SagaState getSagaOrThrow(String sagaId) {
        return sagaStateRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalStateException("Saga not found: " + sagaId));
    }

    /**
     * Loads a {@link BankTransaction} by reference ID or throws {@link IllegalStateException}.
     *
     * @param referenceId the transaction reference ID
     * @return the loaded transaction — never {@code null}
     * @throws IllegalStateException if no transaction exists with the given reference ID
     */
    private BankTransaction findTransactionOrThrow(String referenceId) {
        return bankTransactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction not found for referenceId=" + referenceId));
    }
}

