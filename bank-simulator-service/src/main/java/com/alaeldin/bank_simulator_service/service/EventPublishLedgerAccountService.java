package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.EventType;
import com.alaeldin.bank_simulator_service.dto.OutboxEventRequest;
import com.alaeldin.bank_simulator_service.model.LedgerEvent;
import com.alaeldin.bank_simulator_service.model.OutboxEvent;
import com.alaeldin.bank_simulator_service.model.TransactionLedger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventPublishLedgerAccountService {

    private final OutboxService outboxService;
    @Value("${app.name:bank-simulator-service}")
    private String applicationName;
    @Value("${app.version:1.0}")
    private String applicationVersion;
    private  final RedisTemplate<String,String> redisTemplate;
    private static final long EVENT_TTL_SECONDS = 120; // 2 minutes TTL for Redis keys

    private static final String REDIS_EVENT_PREFIX = "event:";
    private static final String REDIS_PUBLISHED_VALUE = "published_ledger";
    // Constants
    private static final String AGGREGATE_TYPE = "LEDGER";


    @Transactional
    public boolean publishEventWithOutboxSupport(TransactionLedger transaction, EventType eventType)
    {
        String txn = transaction.getTransactionReference();
        log.debug("Publishing ledger event with outbox support: transactionReference={}, eventType={}", txn, eventType);
         String idempotencyKey = generateIdempotencyKey(transaction, eventType);
         log.debug("Generated idempotency key: {}", idempotencyKey);
        try
        {
            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey,REDIS_PUBLISHED_VALUE, EVENT_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(wasSet))
            {
                log.debug("Idempotency key set in Redis, proceeding to publish event: {}", idempotencyKey);
                saveLedgerEventToOutbox(transaction, eventType);
                return true;
            }
            else
            {
                log.warn("Duplicate event detected, skipping publish: transactionReference={}, eventType={}", txn, eventType);
                return false;
            }
        }
        catch(Exception ex)
        {
            log.error("Failed to publish ledger event with outbox support: transactionReference={}, eventType={}, error={}", txn, eventType, ex.getMessage(), ex);
            cleanupIdempotencyKey(idempotencyKey);
            return false;
        }
    }
    @Transactional
    public void saveLedgerEventToOutbox(TransactionLedger transactionLedger, EventType eventType)
    {
          //Validation
        validateInputs(transactionLedger, eventType);
        String accountNumber = transactionLedger.getAccount().getAccountNumber();
        log.debug("Processing ledger event: accountNumber={}, eventType={}", accountNumber, eventType);
        try {
             com.alaeldin.bank_simulator_service.dto.OutboxEventRequest outboxEventRequest = createOutboxEventRequest(transactionLedger, eventType);
             OutboxEvent savedEvent = outboxService.saveEventToOutbox(outboxEventRequest);


             log.info("Ledger event saved to outbox: eventType={}, aggregateId={}, eventType={}", savedEvent.getEventType(), savedEvent.getAggregateId(), savedEvent.getEventType());
        }
        catch (Exception e) {

            log.error("Failed to save ledger event to outbox: accountNumber={}, eventType={}, error={}", accountNumber, eventType, e.getMessage(), e);
            throw new RuntimeException("Failed to save ledger event to outbox", e);
        }
    }

    private OutboxEventRequest createOutboxEventRequest(TransactionLedger transactionLedger, EventType eventType)
    {
        LedgerEvent eventPayload = buildLedgerEvent(transactionLedger, eventType);


        return  OutboxEventRequest
                .builder()
                .aggregateId(transactionLedger.getTransactionReference())
                .aggregateType(AGGREGATE_TYPE)
                .eventType(eventType.name())
                .eventPayload(eventPayload)
                .idempotencyKey(generateIdempotencyKey(transactionLedger, eventType))
                .build();
    }

    public LedgerEvent buildLedgerEvent(TransactionLedger transactionLedger, EventType eventType)
    {
           return LedgerEvent
                   .builder()
                   .eventId(UUID.randomUUID().toString())
                   .eventType(eventType.name())  // Added eventType field
                   .transactionReference(transactionLedger.getTransactionReference())
                   .accountNumber(transactionLedger.getAccount().getAccountNumber())
                   .entryType(eventType.name())
                   .amount(transactionLedger.getAmount())
                   .balanceBefore(transactionLedger.getBalanceBefore())
                   .balanceAfter(transactionLedger.getBalanceAfter())
                   .description(transactionLedger.getDescription())
                   .build();


    }

    private String generateIdempotencyKey(TransactionLedger transactionLedger, EventType eventType)
    {
        return transactionLedger.getTransactionReference() + "-" + eventType.name();
    }
    public void validateInputs(TransactionLedger ledger, EventType eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException(" Event type cannot be null");
        }

        if (ledger == null) {
            throw new IllegalArgumentException(" Ledger cannot be null");
        }
        if (ledger.getAccount().getAccountNumber() == null || ledger.getAccount().getAccountNumber().isEmpty()) {
            throw new IllegalArgumentException(" Account number cannot be null or empty");
        }

    }
      private void cleanupIdempotencyKey(String idempotencyKey)
      {
          try
          {
              Boolean deleted = redisTemplate.delete(idempotencyKey);
                if (Boolean.TRUE.equals(deleted)) {
                    log.debug("Idempotency key cleaned up from Redis: {}", idempotencyKey);
                } else {
                    log.warn("Failed to clean up idempotency key from Redis (key not found): {}", idempotencyKey);
                }
          }
          catch(Exception ex){
              log.warn(" Failed to clean up idempotency key in Redis: error={}", ex.getMessage(), ex);
          }
    }
}
