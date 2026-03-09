package com.alaeldin.bank_simulator_service.repository;

import com.alaeldin.bank_simulator_service.constant.SagaStatus;
import com.alaeldin.bank_simulator_service.model.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link SagaState} entities.
 *
 * <p>Provides CRUD operations (via {@link JpaRepository}) plus custom finders
 * needed by the Saga orchestrator for recovery, monitoring, and compensation flows.</p>
 *
 * <p>ID type is {@code String} because {@code SagaState#sagaId} is a {@code String}
 * (business-generated UUID / correlation ID).</p>
 */
@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, String> {

    // -------------------------------------------------------------------------
    // Lookup by business key
    // -------------------------------------------------------------------------

    /**
     * Finds a saga by its associated transaction reference ID.
     *
     * @param transactionReferenceId the unique transaction reference (e.g. "TXN-2024-001")
     * @return an {@link Optional} containing the saga, or empty if none exists
     */
    Optional<SagaState> findByTransactionReferenceId(String transactionReferenceId);

    /**
     * Checks whether a saga already exists for a given transaction reference.
     * Cheaper than {@link #findByTransactionReferenceId} when only existence matters.
     *
     * @param transactionReferenceId the transaction reference to check
     * @return {@code true} if a saga record exists
     */
    boolean existsByTransactionReferenceId(String transactionReferenceId);

    // -------------------------------------------------------------------------
    // Status-based finders (used by monitoring / admin)
    // -------------------------------------------------------------------------

    /**
     * Returns all sagas in the given status.
     *
     * @param status the saga status to filter by
     * @return list of matching sagas; empty list if none found
     */
    List<SagaState> findByStatus(SagaStatus status);

    /**
     * Returns sagas in the given status that still have remaining retry budget.
     * Used by the recovery scheduler to pick up work that can be retried.
     *
     * @param status     the saga status to filter by
     * @param maxRetries upper bound (exclusive) on retry count
     * @return list of retryable sagas; empty list if none found
     */
    List<SagaState> findByStatusAndRetryCountLessThan(SagaStatus status, int maxRetries);

    // -------------------------------------------------------------------------
    // Recovery queries (used by the stale-saga recovery scheduler)
    // -------------------------------------------------------------------------

    /**
     * Finds sagas that have completed the debit step but whose credit step has not
     * progressed within the given time window, and still have retry budget remaining.
     *
     * <p>These sagas should be re-submitted for the credit step by the recovery scheduler.</p>
     *
     * @param threshold cut-off timestamp; sagas updated before this instant are considered stale
     * @return sagas eligible for debit-completed recovery, ordered oldest-first
     */
    @Query("""
            SELECT s FROM SagaState s
            WHERE s.status = com.alaeldin.bank_simulator_service.constant.SagaStatus.DEBIT_COMPLETED
              AND s.updatedAt < :threshold
              AND s.retryCount < s.maxRetries
            ORDER BY s.updatedAt ASC
            """)
    List<SagaState> findStaleDebitCompletedSagas(@Param("threshold") LocalDateTime threshold);

    /**
     * Finds sagas stuck in the {@code COMPENSATING} state beyond the given threshold.
     *
     * <p>These sagas may need manual intervention or a retry of the compensation step.</p>
     *
     * @param threshold cut-off timestamp; sagas updated before this instant are considered stale
     * @return sagas stuck in compensation, ordered oldest-first
     */
    @Query("""
            SELECT s FROM SagaState s
            WHERE s.status = com.alaeldin.bank_simulator_service.constant.SagaStatus.COMPENSATING
              AND s.updatedAt < :threshold
            ORDER BY s.updatedAt ASC
            """)
    List<SagaState> findStaleCompensatingSagas(@Param("threshold") LocalDateTime threshold);

    /**
     * Finds all non-terminal sagas started before the given threshold — a broad sweep
     * for sagas that never reached a final status ({@code COMPLETED}, {@code FAILED},
     * or {@code COMPENSATED}).
     *
     * @param threshold  cut-off timestamp
     * @param finalStatuses the set of terminal statuses to exclude
     * @return stale in-progress sagas, ordered oldest-first
     */
    @Query("""
            SELECT s FROM SagaState s
            WHERE s.status NOT IN :finalStatuses
              AND s.startedAt < :threshold
            ORDER BY s.startedAt ASC
            """)
    List<SagaState> findStaleInProgressSagas(
            @Param("threshold") LocalDateTime threshold,
            @Param("finalStatuses") List<SagaStatus> finalStatuses);

    // -------------------------------------------------------------------------
    // Aggregate / monitoring
    // -------------------------------------------------------------------------

    /**
     * Counts sagas by status — useful for health-check and monitoring endpoints.
     *
     * @param status the saga status to count
     * @return number of sagas in that status
     */
    long countByStatus(SagaStatus status);

    /**
     * Finds sagas involving the given source account that are not yet in a terminal state.
     * Used to detect whether a new saga can be safely started for this account.
     *
     * @param sourceAccountNumber the source account number
     * @param finalStatuses       statuses considered terminal
     * @return list of active sagas for the account
     */
    @Query("""
            SELECT s FROM SagaState s
            WHERE s.sourceAccountNumber = :accountNumber
              AND s.status NOT IN :finalStatuses
            """)
    List<SagaState> findActiveBySourceAccount(
            @Param("accountNumber") String sourceAccountNumber,
            @Param("finalStatuses") List<SagaStatus> finalStatuses);
}
