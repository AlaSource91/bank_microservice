package com.alaeldin.bank_query_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer configuration for bank-query-service.
 * Configures consumer factory, listener container, error handling, and Dead Letter Queue (DLQ).
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaConsumerConfig {

    // Kafka Connection
    @Value("${spring.kafka.consumer.bootstrap-servers:${spring.kafka.bootstrap-servers}}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // Consumer Configuration
    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${spring.kafka.consumer.max-poll-records:100}")
    private int maxPollRecords;

    @Value("${spring.kafka.consumer.properties.session.timeout.ms:30000}")
    private int sessionTimeoutMs;

    @Value("${spring.kafka.consumer.properties.heartbeat.interval.ms:3000}")
    private int heartbeatIntervalMs;

    @Value("${spring.kafka.consumer.properties.isolation.level:read_committed}")
    private String isolationLevel;

    // Listener Configuration
    @Value("${app.kafka.consumer.concurrency:3}")
    private int concurrency;

    // Error Handling & Retry Configuration
    @Value("${app.kafka.consumer.retry.backoff-interval-ms:2000}")
    private long retryBackoffIntervalMs;

    @Value("${app.kafka.consumer.retry.max-attempts:3}")
    private long retryMaxAttempts;

    @Value("${app.kafka.consumer.dlq.suffix:.DLT}")
    private String dlqSuffix;

    /**
     * Creates and configures the Kafka ConsumerFactory.
     * Sets up consumer properties including bootstrap servers, deserializers,
     * offset reset strategy, and exactly-once semantics.
     *
     * @return configured ConsumerFactory for String key-value pairs
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Connection Configuration
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // Deserializers
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Offset & Commit Configuration
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability

        // Polling Configuration
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);

        // Session Management
        config.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        config.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);

        // Exactly-once semantics - only read committed messages
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevel);

        log.info("Kafka Consumer Factory configured - Bootstrap: {}, GroupId: {}, IsolationLevel: {}, MaxPollRecords: {}",
                bootstrapServers, groupId, isolationLevel, maxPollRecords);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Creates and configures the Kafka Listener Container Factory.
     * Sets up concurrency, acknowledgment mode, and error handling.
     *
     * @param kafkaTemplate KafkaTemplate for sending messages to DLQ
     * @return configured ConcurrentKafkaListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrency);

        // Manual acknowledgment for better control
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Set error handler with DLQ and retry logic
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));

        log.info("Kafka Listener Container Factory configured - Concurrency: {}, AckMode: MANUAL",
                concurrency);

        return factory;
    }

    /**
     * Creates and configures the error handler with Dead Letter Queue (DLQ) support.
     * Implements retry logic with fixed backoff and routes failed messages to DLQ.
     * Non-retryable exceptions (e.g., JsonProcessingException, IllegalArgumentException)
     * are sent directly to DLQ without retry attempts.
     *
     * @param kafkaTemplate KafkaTemplate for publishing to DLQ
     * @return configured DefaultErrorHandler
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {

        // Configure Dead Letter Queue (DLQ) recoverer
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    String dlqTopic = record.topic() + dlqSuffix;
                    log.error("Sending record to DLQ - Topic: {}, Key: {}, Partition: {}, Offset: {}, Exception: {}",
                            dlqTopic, record.key(), record.partition(), record.offset(), exception.getMessage());
                    return new org.apache.kafka.common.TopicPartition(dlqTopic, record.partition());
                }
        );

        // Configure retry backoff policy
        FixedBackOff backoffPolicy = new FixedBackOff(retryBackoffIntervalMs, retryMaxAttempts);

        // Create error handler with recoverer and backoff
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backoffPolicy);

        // Add retry listener for monitoring
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
            log.warn("Retrying record - Topic: {}, Key: {}, Partition: {}, Offset: {}, Attempt: {}/{}, Exception: {}",
                    record.topic(), record.key(), record.partition(), record.offset(),
                    deliveryAttempt, retryMaxAttempts, ex != null ? ex.getMessage() : "Unknown error")
        );

        // Configure non-retryable exceptions (send directly to DLQ)
        errorHandler.addNotRetryableExceptions(
                JsonProcessingException.class,  // Malformed JSON - no point retrying
                IllegalArgumentException.class   // Invalid data - no point retrying
        );

        log.info("Kafka Error Handler configured - RetryAttempts: {}, BackoffInterval: {}ms, DLQ Suffix: {}",
                retryMaxAttempts, retryBackoffIntervalMs, dlqSuffix);

        return errorHandler;
    }
}
