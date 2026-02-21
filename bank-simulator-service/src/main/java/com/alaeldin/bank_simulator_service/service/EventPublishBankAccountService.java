package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.AccountEventType;
import com.alaeldin.bank_simulator_service.dto.OutboxEventRequest;
import com.alaeldin.bank_simulator_service.model.BankAccount;
import com.alaeldin.bank_simulator_service.model.BankAccountEvent;
import com.alaeldin.bank_simulator_service.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service responsible for publishing bank account events through the outbox pattern.
 * This service ensures reliable event publishing by storing events in an outbox table
 * which are then asynchronously published by a separate process.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventPublishBankAccountService {

    // Dependencies
    private final OutboxService outboxService;

    // Configuration
    @Value("${app.name:bank-simulator-service}")
    private String applicationName;

    @Value("${app.version:1.0}")
    private String applicationVersion;

    // Constants
    private static final String AGGREGATE_TYPE = "BANK_ACCOUNT";

    /**
     * Saves a bank account event to the outbox for reliable publishing.
     * This method validates the input and creates an event entry in the outbox table,
     * which will be processed asynchronously by the outbox publisher.
     *
     * @param bankAccount the bank account that triggered the event
     * @param accountEventType the type of event (CREATED, UPDATED, etc.)
     * @throws IllegalArgumentException if validation fails
     * @throws RuntimeException if event cannot be saved to outbox
     */
    @Transactional
    public void saveAccountEventToOutbox(BankAccount bankAccount, AccountEventType accountEventType) {
        // Validation
        validateInputs(bankAccount, accountEventType);

        String accountNumber = bankAccount.getAccountNumber();
        log.debug("Processing account event: accountNumber={}, eventType={}", accountNumber, accountEventType);

        try {
            OutboxEventRequest outboxRequest = createOutboxEventRequest(bankAccount, accountEventType);
            OutboxEvent savedEvent = outboxService.saveEventToOutbox(outboxRequest);

            log.info("Account event saved to outbox: accountNumber={}, eventType={}, outboxId={}",
                    accountNumber, accountEventType, savedEvent.getId());
        } catch (Exception e) {
            log.error("Failed to save account event to outbox: accountNumber={}, eventType={}, error={}",
                    accountNumber, accountEventType, e.getMessage(), e);
            throw new RuntimeException("Failed to save account event to outbox", e);
        }
    }


    /**
     * Validates bank account and event type before processing.
     *
     * @param bankAccount the bank account to validate
     * @param accountEventType the event type to validate
     * @throws IllegalArgumentException if any validation fails
     */
    private void validateInputs(BankAccount bankAccount, AccountEventType accountEventType) {
        if (bankAccount == null) {
            throw new IllegalArgumentException("Bank account cannot be null");
        }

        if (accountEventType == null) {
            throw new IllegalArgumentException("Account event type cannot be null");
        }

        if (bankAccount.getAccountNumber() == null || bankAccount.getAccountNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Bank account number is required");
        }

        if (bankAccount.getAccountHolderName() == null || bankAccount.getAccountHolderName().trim().isEmpty()) {
            throw new IllegalArgumentException("Account holder name is required");
        }

        if (bankAccount.getAccountType() == null) {
            throw new IllegalArgumentException("Account type is required");
        }

        if (bankAccount.getBalance() == null) {
            throw new IllegalArgumentException("Account balance is required");
        }
    }

    /**
     * Creates an OutboxEventRequest for reliable event publishing.
     *
     * @param bankAccount the bank account
     * @param accountEventType the event type
     * @return configured OutboxEventRequest
     */
    private OutboxEventRequest createOutboxEventRequest(BankAccount bankAccount, AccountEventType accountEventType) {
        BankAccountEvent eventPayload = buildAccountEvent(bankAccount, accountEventType);

        return OutboxEventRequest.builder()
                .aggregateId(bankAccount.getAccountNumber())
                .aggregateType(AGGREGATE_TYPE)
                .eventType(accountEventType.name())
                .eventPayload(eventPayload)
                .idempotencyKey(generateIdempotencyKey(bankAccount.getAccountNumber(), accountEventType, bankAccount))
                .build();
    }
    /**
     * Generates an idempotency key for the event.
     * This ensures that duplicate events with the same account number and type won't be processed twice.
     *
     * @param accountNumber the bank account number
     * @param accountEventType the event type
     * @return unique idempotency key
     */
    private String generateIdempotencyKey(String accountNumber, AccountEventType accountEventType, BankAccount bankAccount) {
        return accountNumber + ":" + accountEventType.name() + ":" + bankAccount.getVersion();
    }

    /**
     * Builds a BankAccountEvent payload from a bank account and event type.
     *
     * @param bankAccount the bank account
     * @param accountEventType the event type
     * @return populated BankAccountEvent
     */
    private BankAccountEvent buildAccountEvent(BankAccount bankAccount, AccountEventType accountEventType) {
        return BankAccountEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .accountNumber(bankAccount.getAccountNumber())
                .accountHolderName(bankAccount.getAccountHolderName())
                .accountType(bankAccount.getAccountType())
                .eventType(accountEventType.name())
                .balance(bankAccount.getBalance())
                .timestamp(LocalDateTime.now())
                .applicationName(applicationName)
                .version(applicationVersion)
                .build();
    }
}
