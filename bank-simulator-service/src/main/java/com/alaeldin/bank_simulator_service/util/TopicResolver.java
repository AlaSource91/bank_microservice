package com.alaeldin.bank_simulator_service.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves the target Kafka topic for a given aggregate type.
 *
 * <p>Topic names are injected from application configuration, so they can be
 * overridden per-environment without code changes.
 */
@Component
@Slf4j
public class TopicResolver {

    private final Map<String, String> topicByAggregateType;
    private final String fallbackTopic;

    public TopicResolver(
            @Value("${app.kafka.topic.transaction-events:bank.transaction.events}")  String transactionTopic,
            @Value("${app.kafka.topic.account-events:bank.account.events}")          String accountTopic,
            @Value("${app.kafka.topic.ledger-events:bank.ledger.events}")            String ledgerTopic,
            @Value("${app.kafka.topic.saga-events:bank.saga.events}")                String sagaTopic,
            @Value("${app.kafka.topic.debit-events:bank.debit.events}")              String debitTopic,
            @Value("${app.kafka.topic.credit-events:bank.credit.events}")            String creditTopic,
            @Value("${app.kafka.topic.compensation-events:bank.compensation.events}") String compensationTopic
    ) {
        this.fallbackTopic = transactionTopic;
        this.topicByAggregateType = Map.of(
                "BANK_ACCOUNT",  accountTopic,
                "TRANSACTION",   transactionTopic,
                "LEDGER",        ledgerTopic,
                "SAGA",          sagaTopic,
                "DEBIT",         debitTopic,
                "CREDIT",        creditTopic,
                "COMPENSATION",  compensationTopic
        );
    }

    /**
     * Returns the Kafka topic for the given aggregate type (case-insensitive).
     * Falls back to the transaction-events topic if the aggregate type is unknown.
     *
     * @param aggregateType the aggregate type string stored in the outbox record
     * @return target Kafka topic name
     */
    public String resolve(String aggregateType) {
        if (aggregateType == null) {
            log.warn("Aggregate type is null — falling back to topic '{}'", fallbackTopic);
            return fallbackTopic;
        }
        String topic = topicByAggregateType.get(aggregateType.toUpperCase());
        if (topic == null) {
            log.warn("Unknown aggregate type '{}' — falling back to topic '{}'", aggregateType, fallbackTopic);
            return fallbackTopic;
        }
        return topic;
    }
}

