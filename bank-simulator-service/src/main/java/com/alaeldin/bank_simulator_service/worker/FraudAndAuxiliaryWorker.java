package com.alaeldin.bank_simulator_service.worker;

import com.alaeldin.bank_simulator_service.repository.BankTransactionRepository;
import com.alaeldin.bank_simulator_service.repository.TransactionRepository;
import com.netflix.conductor.sdk.workflow.annotation.WorkerTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Conductor Worker for Fraud Detection and Auxiliary Tasks
 * Handles AML checks, velocity checks, limit checks, logging, and notifications
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FraudAndAuxiliaryWorker {

    private final BankTransactionRepository transactionRepository;
    private final Random random = new Random();

    /**
     * Performs AML (Anti-Money Laundering) check
     *
     * @param accountId Account to check
     * @param amount Transaction amount
     * @return AML check result
     */
    @WorkerTask(value = "aml_check", pollingInterval = 100)
    public Map<String, Object> performAmlCheck(String accountId, Double amount) {
        log.info("[CONDUCTOR-WORKER] Performing AML check for account: {}, amount: {}",
            accountId, amount);

        Map<String, Object> result = new HashMap<>();

        try {
            // Simulate AML check logic
            // In real scenario: check against sanctions lists, PEP databases, etc.
            boolean passed = true;
            String riskLevel = "LOW";

            // High amounts trigger additional scrutiny
            if (amount > 10000) {
                riskLevel = "MEDIUM";
            }
            if (amount > 50000) {
                riskLevel = "HIGH";
                // 10% chance of failing for high-value transactions
                passed = random.nextDouble() > 0.1;
            }

            result.put("passed", passed);
            result.put("riskLevel", riskLevel);
            result.put("checkType", "AML");
            result.put("accountId", accountId);
            result.put("amount", amount);

            if (!passed) {
                result.put("reason", "High-risk transaction flagged for manual review");
            }

            log.info("[CONDUCTOR-WORKER] AML check completed. Passed: {}, Risk: {}", passed, riskLevel);
            return result;

        } catch (Exception e) {
            log.error("[CONDUCTOR-WORKER] AML check failed", e);
            throw new RuntimeException("AML check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks transaction velocity (frequency) limits
     *
     * @param accountId Account to check
     * @param timeWindow Time window (e.g., "24h")
     * @return Velocity check result
     */
    @WorkerTask(value = "velocity_check", pollingInterval = 100)
    public Map<String, Object> performVelocityCheck(String accountId, String timeWindow) {
        log.info("[CONDUCTOR-WORKER] Performing velocity check for account: {}, window: {}",
            accountId, timeWindow);

        Map<String, Object> result = new HashMap<>();

        try {
            // Simulate velocity check
            // In real scenario: count transactions in the given time window
            long transactionCount = random.nextInt(30);  // Simulate 0-30 transactions
            double totalAmount = random.nextDouble() * 100000;  // Simulate total amount

            // Velocity limits: max 50 transactions or $100,000 in 24h
            boolean passed = transactionCount < 50 && totalAmount < 100000;

            result.put("passed", passed);
            result.put("checkType", "VELOCITY");
            result.put("accountId",accountId);
            result.put("timeWindow", timeWindow);
            result.put("transactionCount", transactionCount);
            result.put("totalAmount", totalAmount);

            if (!passed) {
                result.put("reason", "Transaction velocity limit exceeded");
            }

            log.info("[CONDUCTOR-WORKER] Velocity check completed. Passed: {}, Count: {}",
                passed, transactionCount);
            return result;

        } catch (Exception e) {
            log.error("[CONDUCTOR-WORKER] Velocity check failed", e);
            throw new RuntimeException("Velocity check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks transaction amount limits
     *
     * @param accountId Account to check
     * @param amount Transaction amount
     * @return Limit check result
     */
    @WorkerTask(value = "transaction_limit_check", pollingInterval = 100)
    public Map<String, Object> performLimitCheck(String accountId, Double amount) {
        log.info("[CONDUCTOR-WORKER] Performing limit check for account: {}, amount: {}",
            accountId, amount);

        Map<String, Object> result = new HashMap<>();

        try {
            // Transaction limits
            final double DAILY_LIMIT = 50000.0;
            final double PER_TRANSACTION_LIMIT = 10000.0;

            boolean passed = amount <= PER_TRANSACTION_LIMIT;

            result.put("passed", passed);
            result.put("checkType", "LIMIT");
            result.put("accountId", accountId);
            result.put("amount", amount);
            result.put("perTransactionLimit", PER_TRANSACTION_LIMIT);
            result.put("dailyLimit", DAILY_LIMIT);

            if (!passed) {
                result.put("reason", "Transaction amount exceeds per-transaction limit");
            }

            log.info("[CONDUCTOR-WORKER] Limit check completed. Passed: {}, Amount: {}",
                passed, amount);
            return result;

        } catch (Exception e) {
            log.error("[CONDUCTOR-WORKER] Limit check failed", e);
            throw new RuntimeException("Limit check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Publishes transfer completed event to Kafka
     *
     * @param sourceAccountId Source account
     * @param destinationAccountId Destination account
     * @param amount Transfer amount
     * @param debitTransactionId Debit transaction ID
     * @param creditTransactionId Credit transaction ID
     * @param status Transfer status
     * @return Success result
     */
    @WorkerTask(value = "publish_transfer_completed_event", pollingInterval = 100)
    public Map<String, Object> publishTransferCompletedEvent(
            String sourceAccountId,
            String destinationAccountId,
            Double amount,
            Long debitTransactionId,
            Long creditTransactionId,
            String status) {

        log.info("[CONDUCTOR-WORKER] Publishing transfer completed event");

        Map<String, Object> result = new HashMap<>();

        try {
            // In real scenario: publish to Kafka
            // For now, just log the event
            log.info("[CONDUCTOR-WORKER] Transfer Completed Event: Source={}, Dest={}, Amount={}, Status={}",
                sourceAccountId, destinationAccountId, amount, status);

            result.put("published", true);
            result.put("eventType", "TRANSFER_COMPLETED");
            result.put("sourceAccountId", sourceAccountId);
            result.put("destinationAccountId", destinationAccountId);
            result.put("amount", amount);
            result.put("status", status);

            return result;

        } catch (Exception e) {
            log.error("[CONDUCTOR-WORKER] Failed to publish event", e);
            // Don't throw - this is optional task
            result.put("published", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Checks if debit was completed in a failed workflow (for compensation)
     *
     * @param workflowId Failed workflow ID
     * @return Debit completion status
     */
    @WorkerTask(value = "check_debit_completed", pollingInterval = 100)
    public Map<String, Object> checkDebitCompleted(String workflowId) {
        log.info("[CONDUCTOR-WORKER] Checking if debit was completed in workflow: {}", workflowId);

        Map<String, Object> result = new HashMap<>();

        try {
            // In real scenario: query workflow execution details from Conductor
            // For now, simulate the check
            boolean debitCompleted = true;  // Assume debit was completed

            result.put("debitCompleted", debitCompleted);
            result.put("workflowId", workflowId);

            if (debitCompleted) {
                // Add mock data - in real scenario, fetch from workflow output
                result.put("sourceAccountId", "1");
                result.put("amount", 1000.0);
                result.put("debitTransactionId", 12345L);
            }

            log.info("[CONDUCTOR-WORKER] Debit completion check result: {}", debitCompleted);
            return result;

        } catch (Exception e) {
            log.error("[CONDUCTOR-WORKER] Check debit completed failed", e);
            throw new RuntimeException("Check debit completed failed: " + e.getMessage(), e);
        }
    }

    /**
     * Logs compensation action
     *
     * @param action Compensation action performed
     * @param transactionId Transaction ID
     * @return Log result
     */
    @WorkerTask(value = "log_compensation", pollingInterval = 100)
    public Map<String, Object> logCompensation(String action, Long transactionId) {
        log.info("[CONDUCTOR-WORKER] Logging compensation: action={}, txId={}", action, transactionId);

        Map<String, Object> result = new HashMap<>();
        result.put("logged", true);
        result.put("action", action);
        result.put("transactionId", transactionId);
        return result;
    }

    /**
     * Logs that no compensation is needed
     *
     * @param action Action type
     * @param reason Reason
     * @return Log result
     */
    @WorkerTask(value = "log_no_compensation_needed", pollingInterval = 100)
    public Map<String, Object> logNoCompensationNeeded(String action, String reason) {
        log.info("[CONDUCTOR-WORKER] Logging no compensation needed: action={}, reason={}",
            action, reason);

        Map<String, Object> result = new HashMap<>();
        result.put("logged", true);
        result.put("action", action);
        result.put("reason", reason);
        return result;
    }

    /**
     * Notifies users about transfer failure
     *
     * @param sourceAccountId Source account
     * @param destinationAccountId Destination account
     * @param status Status
     * @return Notification result
     */
    @WorkerTask(value = "notify_failure", pollingInterval = 100)
    public Map<String, Object> notifyFailure(
            String sourceAccountId,
            String destinationAccountId,
            String status) {

        log.info("[CONDUCTOR-WORKER] Sending failure notification: Source={}, Dest={}, Status={}",
            sourceAccountId, destinationAccountId, status);

        Map<String, Object> result = new HashMap<>();
        result.put("notified", true);
        result.put("sourceAccountId", sourceAccountId);
        result.put("destinationAccountId", destinationAccountId);
        result.put("status", status);
        return result;
    }

    /**
     * Rejects transfer due to fraud checks failure
     *
     * @param reason Rejection reason
     * @param amlPassed AML check result
     * @param velocityPassed Velocity check result
     * @param limitPassed Limit check result
     * @return Rejection result
     */
    @WorkerTask(value = "reject_transfer", pollingInterval = 100)
    public Map<String, Object> rejectTransfer(
            String reason,
            Boolean amlPassed,
            Boolean velocityPassed,
            Boolean limitPassed) {

        log.warn("[CONDUCTOR-WORKER] Rejecting transfer: reason={}, AML={}, Velocity={}, Limit={}",
            reason, amlPassed, velocityPassed, limitPassed);

        Map<String, Object> result = new HashMap<>();
        result.put("rejected", true);
        result.put("reason", reason);
        result.put("amlPassed", amlPassed);
        result.put("velocityPassed", velocityPassed);
        result.put("limitPassed", limitPassed);
        return result;
    }

    /**
     * Notifies about fraud-related rejection
     *
     * @param sourceAccountId Source account
     * @param reason Rejection reason
     * @return Notification result
     */
    @WorkerTask(value = "notify_fraud_rejection", pollingInterval = 100)
    public Map<String, Object> notifyFraudRejection(String sourceAccountId, String reason) {
        log.info("[CONDUCTOR-WORKER] Sending fraud rejection notification: Account={}, Reason={}",
            sourceAccountId, reason);

        Map<String, Object> result = new HashMap<>();
        result.put("notified", true);
        result.put("accountId", sourceAccountId);
        result.put("reason", reason);
        return result;
    }
}

