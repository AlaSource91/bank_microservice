package com.alaeldin.bank_simulator_service.worker;

import com.alaeldin.bank_simulator_service.model.Account;
import com.alaeldin.bank_simulator_service.model.Transaction;
import com.alaeldin.bank_simulator_service.repository.AccountRepository;
import com.alaeldin.bank_simulator_service.repository.BankAccountRepository;
import com.alaeldin.bank_simulator_service.repository.TransactionRepository;
import com.alaeldin.bank_simulator_service.service.EventPublishingService;
import com.netflix.conductor.sdk.workflow.annotation.WorkerTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Conductor Worker for Transaction Operations
 * Handles debit, credit, and reversal operations
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionWorker {

    private BankAccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EventPublishingService eventPublishingService;

    /**
     * Debits amount from source account
     *
     * @param accountId Account to debit
     * @param amount Amount to debit
     * @param currency Currency
     * @param workflowId Workflow ID for tracking
     * @param taskId Task ID for idempotency
     * @return Debit transaction result
     */
    @WorkerTask(value = "debit_source_account", pollingInterval = 100)
    @Transactional
    public Map<String, Object> debitSourceAccount(
            String accountId,
            Double amount,
            String currency,
            String workflowId,
            String taskId) {

        log.info("[CONDUCTOR-WORKER] Debiting account: {}, amount: {}, workflowId: {}",
            accountId, amount, workflowId);

        Map<String, Object> result = new HashMap<>();

        try {
            // Check for idempotency - has this task already been processed?
            Transaction existing = transactionRepository.findByExternalReferenceId(taskId);
            if (existing != null) {
                log.info("[CONDUCTOR-WORKER] Task already processed (idempotent), returning cached result");
                return buildTransactionResult(existing, "DEBIT");
            }

            // Find account
            Account account = accountRepository.findById(Long.parseLong(accountId))
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

            // Check balance
            BigDecimal amountBD = BigDecimal.valueOf(amount);
            if (account.getBalance().compareTo(amountBD) < 0) {
                throw new RuntimeException("Insufficient balance");
            }

            // Debit account
            BigDecimal oldBalance = account.getBalance();
            account.setBalance(oldBalance.subtract(amountBD));
            account.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(account);

            // Create transaction record
            Transaction transaction = new Transaction();
            transaction.setAccountId(Long.parseLong(accountId));
            transaction.setAmount(amountBD);
            transaction.setType("DEBIT");
            transaction.setStatus("COMPLETED");
            transaction.setCurrency(currency);
            transaction.setBalanceBefore(oldBalance);
            transaction.setBalanceAfter(account.getBalance());
            transaction.setExternalReferenceId(taskId);
            transaction.setMetadata("workflowId:" + workflowId);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction = transactionRepository.save(transaction);

            // Publish event to Kafka
            try {
                eventPublishingService.publishDebitEvent(transaction);
            } catch (Exception e) {
                log.warn("[CONDUCTOR-WORKER] Failed to publish debit event, continuing...", e);
            }

            result = buildTransactionResult(transaction, "DEBIT");
            log.info("[CONDUCTOR-WORKER] Debit successful. TransactionId: {}", transaction.getId());

            return result;

        } catch (Exception e) {
            log.error("[CONDUCTOR-WORKER] Debit failed for account: {}", accountId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Debit operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Credits amount to destination account
     *
     * @param accountId Account to credit
     * @param amount Amount to credit
     * @param currency Currency
     * @param workflowId Workflow ID for tracking
     * @param taskId Task ID for idempotency
     * @param debitTransactionId Related debit transaction
     * @return Credit transaction result
     */
    @WorkerTask(value = "credit_destination_account", pollingInterval = 100)
    @Transactional
    public Map<String, Object> creditDestinationAccount(
            String accountId,
            Double amount,
            String currency,
            String workflowId,
            String taskId,
            Long debitTransactionId) {

        log.info("[CONDUCTOR-WORKER] Crediting account: {}, amount: {}, workflowId: {}",
            accountId, amount, workflowId);

        Map<String, Object> result = new HashMap<>();

        try {
            // Check for idempotency
            Transaction existing = transactionRepository.findByExternalReferenceId(taskId);
            if (existing != null) {
                log.info("[CONDUCTOR-WORKER] Task already processed (idempotent), returning cached result");
                return buildTransactionResult(existing, "CREDIT");
            }

            // Find account
            Account account = accountRepository.findById(Long.parseLong(accountId))
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

            // Credit account
            BigDecimal amountBD = BigDecimal.valueOf(amount);
            BigDecimal oldBalance = account.getBalance();
            account.setBalance(oldBalance.add(amountBD));
            account.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(account);

            // Create transaction record
            Transaction transaction = new Transaction();
            transaction.setAccountId(Long.parseLong(accountId));
            transaction.setAmount(amountBD);
            transaction.setType("CREDIT");
            transaction.setStatus("COMPLETED");
            transaction.setCurrency(currency);
            transaction.setBalanceBefore(oldBalance);
            transaction.setBalanceAfter(account.getBalance());
            transaction.setExternalReferenceId(taskId);
            transaction.setMetadata("workflowId:" + workflowId + ",debitTxId:" + debitTransactionId);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction = transactionRepository.save(transaction);

            // Publish event to Kafka
            try {
                eventPublishingService.publishCreditEvent(transaction);
            } catch (Exception e) {
                log.warn("[CONDUCTOR-WORKER] Failed to publish credit event, continuing...", e);
            }

            result = buildTransactionResult(transaction, "CREDIT");
            log.info("[CONDUCTOR-WORKER] Credit successful. TransactionId: {}", transaction.getId());

            return result;

        } catch (Exception e) {
            log.error("[CONDUCTOR-WORKER] Credit failed for account: {}", accountId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Credit operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reverses a debit transaction (compensation)
     *
     * @param accountId Account to reverse debit
     * @param amount Amount to reverse
     * @param originalTransactionId Original transaction ID
     * @param reason Reversal reason
     * @return Reversal result
     */
    @WorkerTask(value = "reverse_debit", pollingInterval = 100)
    @Transactional
    public Map<String, Object> reverseDebit(
            String accountId,
            Double amount,
            Long originalTransactionId,
            String reason) {

        log.info("[CONDUCTOR-WORKER] Reversing debit for account: {}, amount: {}, original TX: {}",
            accountId, amount, originalTransactionId);

        Map<String, Object> result = new HashMap<>();

        try {
            // Find account
            Account account = accountRepository.findById(Long.parseLong(accountId))
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

            // Credit back the debited amount (reversal = credit)
            BigDecimal amountBD = BigDecimal.valueOf(amount);
            BigDecimal oldBalance = account.getBalance();
            account.setBalance(oldBalance.add(amountBD));
            account.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(account);

            // Create reversal transaction
            Transaction reversal = new Transaction();
            reversal.setAccountId(Long.parseLong(accountId));
            reversal.setAmount(amountBD);
            reversal.setType("REVERSAL");
            reversal.setStatus("COMPLETED");
            reversal.setCurrency(account.getCurrency());
            reversal.setBalanceBefore(oldBalance);
            reversal.setBalanceAfter(account.getBalance());
            reversal.setMetadata("originalTxId:" + originalTransactionId + ",reason:" + reason);
            reversal.setCreatedAt(LocalDateTime.now());
            reversal = transactionRepository.save(reversal);

            // Publish compensation event
            try {
                eventPublishingService.publishCompensationEvent(reversal);
            } catch (Exception e) {
                log.warn("[CONDUCTOR-WORKER] Failed to publish compensation event, continuing...", e);
            }

            result.put("success", true);
            result.put("reversalTransactionId", reversal.getId());
            result.put("accountId", accountId);
            result.put("amount", amount);
            result.put("newBalance", account.getBalance().doubleValue());
            result.put("reason", reason);

            log.info("[CONDUCTOR-WORKER] Debit reversed successfully. Reversal TX: {}", reversal.getId());
            return result;

        } catch (Exception e) {
            log.error("[CONDUCTOR-WORKER] Reversal failed for account: {}", accountId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            throw new RuntimeException("Reversal operation failed: " + e.getMessage(), e);
        }
    }

    // Helper method to build transaction result
    private Map<String, Object> buildTransactionResult(Transaction transaction, String type) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("transactionId", transaction.getId());
        result.put("accountId", transaction.getAccountId());
        result.put("amount", transaction.getAmount().doubleValue());
        result.put("type", type);
        result.put("currency", transaction.getCurrency());
        result.put("balanceBefore", transaction.getBalanceBefore().doubleValue());
        result.put("newBalance", transaction.getBalanceAfter().doubleValue());
        result.put("timestamp", transaction.getCreatedAt().toString());
        result.put("status", transaction.getStatus());
        return result;
    }
}

