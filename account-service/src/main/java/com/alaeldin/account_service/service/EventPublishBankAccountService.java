package com.alaeldin.account_service.service;


import com.alaeldin.account_service.constant.AccountEventType;
import com.alaeldin.account_service.dto.OutBoxEventRequest;
import com.alaeldin.account_service.model.AccountEvent;
import com.alaeldin.account_service.model.BankAccount;
import com.alaeldin.account_service.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventPublishBankAccountService {

    //Dependencies
    private final OutBoxService outboxService;
    //Configuration
    @Value("${app.name:bank-account-service}")
    private String applicationName;
    @Value("${app.version:1.0}")
    private String applicationVersion;

    //constant
    private static final String AGGREGATE_TYPE = "ACCOUNT";

    @Transactional
    public void saveAccountEventToOutbox(BankAccount bankAccount
            , AccountEventType accountEventType) {
        //Validation
        validateInputs(bankAccount, accountEventType);
        String accountNumber = bankAccount.getAccountNumber();
        log.debug("Processing account event: accountNumber={}, eventType={}", accountNumber, accountEventType);
        try {
            OutBoxEventRequest outboxRequest = createOutboxEventRequest(
                    bankAccount, accountEventType
            );

            OutboxEvent savedEvent = outboxService.saveEvent(
                    outboxRequest
            );

            log.info("Account event saved to outbox: accountNumber={}, eventType={}  , outboxId", accountNumber, accountEventType, savedEvent.getId());
        } catch (Exception e) {
            log.error("Failed to save account event to outbox: accountNumber={}, eventType={}, error={}",
                    accountNumber, accountEventType, e.getMessage(), e);
            throw new RuntimeException("Failed to save account event to outbox", e);
        }

    }

    /**
     * Validates bank account and event type before processing.
     *
     * @param bankAccount      the bank account to validate
     * @param accountEventType the event type to validate
     * @throws IllegalArgumentException if any validation fails
     */
    private OutBoxEventRequest createOutboxEventRequest(BankAccount bankAccount, AccountEventType accountEventType) {

       AccountEvent eventPayload =buildAccountEvent(bankAccount
               ,accountEventType);

       return OutBoxEventRequest
               .builder()
               .aggregateId(bankAccount.getAccountNumber())
               .aggregateType(AGGREGATE_TYPE)
               .eventType(accountEventType.name())
               .eventPayload(eventPayload)
               .idempotencyKey(generateIdempotencyKey(
                       bankAccount.getAccountNumber() ,
                       accountEventType ,
                       bankAccount
               ))
               .build();
    }

    private AccountEvent buildAccountEvent(BankAccount bankAccount, AccountEventType accountEventType) {

     return AccountEvent.builder()
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

    private void validateInputs(BankAccount bankAccount, AccountEventType accountEventType) {

        if (bankAccount == null) {
            throw new IllegalArgumentException("Bank account is required");
        }
        if (accountEventType == null) {
            throw new IllegalArgumentException("AccountEventType is required");
        }

        if (bankAccount.getAccountNumber() == null || bankAccount.getAccountNumber().isEmpty()) {

            throw new IllegalArgumentException("AccountNumber is required");
        }

        if(bankAccount.getAccountHolderName() == null || bankAccount.getAccountHolderName().isEmpty())
        {
            throw new IllegalArgumentException("AccountHolderName is required");
        }

        if (bankAccount.getAccountType() == null) {
            throw new IllegalArgumentException("Account type is required");
        }

        if (bankAccount.getBalance() == null) {
            throw new IllegalArgumentException("Account balance is required");
        }

    }
}