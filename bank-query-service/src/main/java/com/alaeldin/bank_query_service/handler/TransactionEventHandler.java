package com.alaeldin.bank_query_service.handler;

import com.alaeldin.bank_query_service.config.TransactionCacheEvictor;
import com.alaeldin.bank_query_service.model.event.TransactionEvent;
import com.alaeldin.bank_query_service.model.readmodel.TransactionReadModel;
import com.alaeldin.bank_query_service.repository.AccountReadModelRepository;
import com.alaeldin.bank_query_service.repository.TransactionReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

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
    private final AccountReadModelRepository accountReadModelRepository;

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
        updateAccountBalances(event);
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
    // Balance update on successful transfer
    // -------------------------------------------------------------------------

    /**
     * Updates the balance of both source and destination accounts in the read model
     * after a successful transfer. Uses {@code sourceBalanceAfter} and
     * {@code destinationBalanceAfter} carried inside the event.
     *
     * @param event the completed transaction event
     */
    @Caching(evict = {
            @CacheEvict(value = "accountDetails",  key = "#event.sourceAccount.trim().toUpperCase()"),
            @CacheEvict(value = "accountBalance",  key = "#event.sourceAccount.trim().toUpperCase()"),
            @CacheEvict(value = "accountDetails",  key = "#event.destinationAccount.trim().toUpperCase()"),
            @CacheEvict(value = "accountBalance",  key = "#event.destinationAccount.trim().toUpperCase()"),
            @CacheEvict(value = "accountSearch",   allEntries = true),
            @CacheEvict(value = "allAccounts",     allEntries = true)
    })
    public void updateAccountBalances(TransactionEvent event) {

        LocalDateTime now = event.getCompletedAt() != null ? event.getCompletedAt() : LocalDateTime.now();

        // Update source account balance
        if (event.getSourceAccount() != null && event.getSourceBalanceAfter() != null) {
            accountReadModelRepository.findByAccountNumber(event.getSourceAccount())
                    .ifPresentOrElse(
                            source -> {
                                source.setBalance(event.getSourceBalanceAfter());
                                source.setUpdatedAt(now);
                                accountReadModelRepository.save(source);
                                log.info("Updated source account balance - account: {}, newBalance: {}",
                                        event.getSourceAccount(), event.getSourceBalanceAfter());
                            },
                            () -> log.warn("Source account not found in read model - account: {}. " +
                                    "Balance update skipped.", event.getSourceAccount())
                    );
        }

        // Update destination account balance
        if (event.getDestinationAccount() != null && event.getDestinationBalanceAfter() != null) {
            accountReadModelRepository.findByAccountNumber(event.getDestinationAccount())
                    .ifPresentOrElse(
                            destination -> {
                                destination.setBalance(event.getDestinationBalanceAfter());
                                destination.setUpdatedAt(now);
                                accountReadModelRepository.save(destination);
                                log.info("Updated destination account balance - account: {}, newBalance: {}",
                                        event.getDestinationAccount(), event.getDestinationBalanceAfter());
                            },
                            () -> log.warn("Destination account not found in read model - account: {}. " +
                                    "Balance update skipped.", event.getDestinationAccount())
                    );
        }
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
