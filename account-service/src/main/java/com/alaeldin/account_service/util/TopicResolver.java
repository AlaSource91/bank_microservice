package com.alaeldin.account_service.util;

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
            @Value("${app.kafka.topic.account-events:bank.account.events}")String accountTopic

    ) {
        this.fallbackTopic = accountTopic;
        this.topicByAggregateType = Map.of(
                "BANK_ACCOUNT",  accountTopic


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


