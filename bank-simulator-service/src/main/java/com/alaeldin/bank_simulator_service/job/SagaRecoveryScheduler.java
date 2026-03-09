package com.alaeldin.bank_simulator_service.job;

import com.alaeldin.bank_simulator_service.constant.SagaStatus;
import com.alaeldin.bank_simulator_service.model.SagaState;
import com.alaeldin.bank_simulator_service.repository.SagaStateRepository;
import com.alaeldin.bank_simulator_service.service.SagaOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled recovery poller for stale sagas.
 *
 * <p>Three independent sweeps run on staggered delays so they do not
 * compete for the same rows at the same instant:
 * <ol>
 *   <li>{@link #recoverStaleDebitSagas()} — re-drives sagas stuck in
 *       {@code DEBIT_COMPLETED} by retrying the credit step.</li>
 *   <li>{@link #recoverStaleCompensatingSagas()} — re-drives sagas stuck
 *       in {@code COMPENSATING} by retrying the debit reversal.</li>
 *   <li>{@link #logSagaStatistics()} — emits a periodic status summary
 *       for monitoring dashboards.</li>
 * </ol>
 *
 * <p>{@code @EnableScheduling} is declared centrally in
 * {@code SchedulingAndRetryConfig} — not repeated here.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SagaRecoveryScheduler {

    private final SagaStateRepository sagaStateRepository;
    private final SagaOrchestrationService sagaOrchestrationService;

    @Value("${app.saga.recovery.stale-threshold-minutes:10}")
    private int staleThresholdMinutes;

    // -------------------------------------------------------------------------
    // Sweep 1 — stuck at DEBIT_COMPLETED → retry credit step
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${app.saga.recovery.debit-poll-ms:300000}",
               initialDelayString = "${app.saga.recovery.debit-initial-delay-ms:120000}")
    public void recoverStaleDebitSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleThresholdMinutes);
        try {
            List<SagaState> stale = sagaStateRepository.findStaleDebitCompletedSagas(threshold);
            if (stale.isEmpty()) {
                log.debug("No stale DEBIT_COMPLETED sagas found");
                return;
            }
            log.info("Recovering {} stale DEBIT_COMPLETED saga(s)", stale.size());
            stale.forEach(this::retryDebitCompletedSaga);
        } catch (Exception e) {
            log.error("Error during DEBIT_COMPLETED saga recovery sweep: {}", e.getMessage(), e);
        }
    }

    /**
     * Retries the credit step for a single saga stuck in {@code DEBIT_COMPLETED}.
     * If the saga has exhausted its retry budget the saga failure handler inside
     * {@link SagaOrchestrationService} will take care of compensation.
     */
    private void retryDebitCompletedSaga(SagaState saga) {
        log.info("Retrying credit step: sagaId={}, source={}, updatedAt={}",
                saga.getSagaId(), saga.getSourceAccountNumber(), saga.getUpdatedAt());
        try {
            sagaOrchestrationService.executeCreditStep(saga.getSagaId());
        } catch (Exception e) {
            log.error("Credit-step retry failed: sagaId={}, error={}", saga.getSagaId(), e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Sweep 2 — stuck at COMPENSATING → retry compensation
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${app.saga.recovery.compensation-poll-ms:300000}",
               initialDelayString = "${app.saga.recovery.compensation-initial-delay-ms:180000}")
    public void recoverStaleCompensatingSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleThresholdMinutes);
        try {
            List<SagaState> stale = sagaStateRepository.findStaleCompensatingSagas(threshold);
            if (stale.isEmpty()) {
                log.debug("No stale COMPENSATING sagas found");
                return;
            }
            log.info("Recovering {} stale COMPENSATING saga(s)", stale.size());
            stale.forEach(this::retryCompensatingSaga);
        } catch (Exception e) {
            log.error("Error during COMPENSATING saga recovery sweep: {}", e.getMessage(), e);
        }
    }

    /**
     * Retries debit compensation for a single saga stuck in {@code COMPENSATING}.
     */
    private void retryCompensatingSaga(SagaState saga) {
        log.info("Retrying compensation: sagaId={}, source={}, updatedAt={}",
                saga.getSagaId(), saga.getSourceAccountNumber(), saga.getUpdatedAt());
        try {
            sagaOrchestrationService.compensateDebit(saga.getSagaId(), "Recovery retry");
        } catch (Exception e) {
            log.error("Compensation retry failed: sagaId={}, error={}", saga.getSagaId(), e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Sweep 3 — periodic statistics log
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelayString  = "${app.saga.recovery.stats-poll-ms:900000}",
               initialDelayString = "${app.saga.recovery.stats-initial-delay-ms:60000}")
    public void logSagaStatistics() {
        try {
            log.info("Saga stats: PENDING={} | DEBIT_COMPLETED={} | COMPLETED={} | COMPENSATING={} | COMPENSATED={} | FAILED={}",
                    sagaStateRepository.countByStatus(SagaStatus.PENDING),
                    sagaStateRepository.countByStatus(SagaStatus.DEBIT_COMPLETED),
                    sagaStateRepository.countByStatus(SagaStatus.COMPLETED),
                    sagaStateRepository.countByStatus(SagaStatus.COMPENSATING),
                    sagaStateRepository.countByStatus(SagaStatus.COMPENSATED),
                    sagaStateRepository.countByStatus(SagaStatus.FAILED));
        } catch (Exception e) {
            log.error("logSagaStatistics failed: {}", e.getMessage(), e);
        }
    }
}
