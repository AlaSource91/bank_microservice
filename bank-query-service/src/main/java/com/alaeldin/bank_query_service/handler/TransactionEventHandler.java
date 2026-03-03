package com.alaeldin.bank_query_service.handler;

import com.alaeldin.bank_query_service.config.TransactionCacheEvictor;
import com.alaeldin.bank_query_service.model.event.TransactionEvent;
import com.alaeldin.bank_query_service.model.readmodel.TransactionReadModel;
import com.alaeldin.bank_query_service.repository.TransactionReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Handles incoming transaction domain events and projects them into the
 * {@link TransactionReadModel} read-model collection (CQRS read-side).
 *
 * <p>Cache eviction is delegated to {@link TransactionCacheEvictor} after a
 * successful save so that subsequent queries reflect the latest state.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionEventHandler {

    private final TransactionReadModelRepository transactionReadModelRepository;
    private final TransactionCacheEvictor transactionCacheEvictor;
    private final AccountStatisticEventHandler accountStatisticEventHandler;

    // -------------------------------------------------------------------------
    // Public event-handling methods
    // -------------------------------------------------------------------------

    /**
     * Projects a successful transaction event into the read model.
     *
     * @param event the transaction-succeeded domain event
     */
    public void handleTransactionSuccessful(TransactionEvent event) {
        log.info("Handling TransactionSuccessful event - transactionId: {}", event.getTransactionId());
        TransactionReadModel model = buildReadModel(event);
        saveAndEvict(model, event);
        accountStatisticEventHandler.handleTransactionCompleted(event);

    }

    /**
     * Projects a failed transaction event into the read model.
     *
     * @param event the transaction-failed domain event
     */
    public void handleTransactionFailed(TransactionEvent event) {
        log.info("Handling TransactionFailed event - transactionId: {}", event.getTransactionId());
        TransactionReadModel model = buildReadModel(event);
        saveAndEvict(model, event);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a {@link TransactionEvent} to a {@link TransactionReadModel}.
     * Both successful and failed events share the same projection — the
     * {@code status}, {@code failedAt}, and {@code failureReason} fields
     * naturally carry the event-specific values (null for successful ones).
     */
    private TransactionReadModel buildReadModel(TransactionEvent event) {
        return TransactionReadModel.builder()
                .id(event.getId())
                .transactionId(event.getTransactionId())
                .sourceAccountNumber(event.getSourceAccount())
                .sourceAccountHolderName(event.getSourceAccountHolderName())
                .destinationAccountNumber(event.getDestinationAccount())
                .destinationAccountHolderName(event.getDestinationAccountHolderName())
                .amount(event.getAmount())
                .description(event.getDescription())
                .transactionDate(event.getTransactionDate())
                .status(event.getStatusTransaction())
                .failedAt(event.getFailedAt())
                .failureReason(event.getFailureReason())
                .sourceBalanceAfter(event.getSourceBalanceAfter())
                .destinationBalanceAfter(event.getDestinationBalanceAfter())
                .createdAt(event.getCreatedAt())
                .completedAt(event.getCompletedAt())
                .build();
    }

    /**
     * Persists the read model and evicts related cache entries on success.
     * Duplicate-key events are silently skipped (idempotent consumers).
     * Any other persistence error is logged but does not propagate, allowing
     * the Kafka consumer to continue processing subsequent messages.
     *
     * @param model the read model document to persist
     * @param event the originating event (used for cache eviction)
     */
    @Transactional
    public void saveAndEvict(TransactionReadModel model, TransactionEvent event) {
        try {
            transactionReadModelRepository.save(model);
            log.info("Transaction read model saved - transactionId: {}", event.getTransactionId());
            // Evict AFTER successful commit
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                transactionCacheEvictor.evict(event);
                            }
                        }
                );
            }
            else {
                log.warn("No active transaction found, evicting cache immediately - transactionId: {}. " +
                        "This may lead to stale cache if the save operation fails.", event.getTransactionId());
            }
            // transactionCacheEvictor.evict(event);
        } catch (DuplicateKeyException e) {
            log.warn("Duplicate transaction detected, skipping save - transactionId: {}", event.getTransactionId());
            throw e;
        } catch (Exception e) {
            log.error("Failed to persist transaction read model - transactionId: {}, error: {}",
                    event.getTransactionId(), e.getMessage(), e);
            throw e;
        }
    }
}
