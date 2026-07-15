package com.alaeldin.Auth_service.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for the Auth Service.
 *
 * <p>Provides a reliable, idempotent producer with the same settings used by
 * the bank-simulator-service ({@code acks=all}, {@code retries=3},
 * {@code enable.idempotence=true}) so that auth events published to
 * {@code bank.auth.events} are delivered exactly once.</p>
 *
 * <p>Key reliability settings:</p>
 * <ul>
 *   <li>{@code acks=all}            — wait for all in-sync replicas to acknowledge</li>
 *   <li>{@code retries=3}           — retry on transient failures</li>
 *   <li>{@code enable.idempotence=true} — exactly-once semantics at the producer level</li>
 *   <li>{@code batch.size=16384}    — batch up to 16 KB for throughput</li>
 *   <li>{@code linger.ms=5}         — wait up to 5 ms for batch fill</li>
 * </ul>
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Creates the Kafka {@link ProducerFactory} with reliability and performance settings.
     *
     * @return configured {@link ProducerFactory} for String key/value messages
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // ── Connectivity ─────────────────────────────────────────────────────
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // ── Reliability ──────────────────────────────────────────────────────
        config.put(ProducerConfig.ACKS_CONFIG,               "all");
        config.put(ProducerConfig.RETRIES_CONFIG,            3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // ── Performance ──────────────────────────────────────────────────────
        config.put(ProducerConfig.BATCH_SIZE_CONFIG,    16384);
        config.put(ProducerConfig.LINGER_MS_CONFIG,     5);
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        // ── Timeouts ─────────────────────────────────────────────────────────
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,       30000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,      120000);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,             10000);
        config.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG,  600000);
        config.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG,     50);
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG,         100);

        log.info("[KafkaConfig] Kafka producer configured for servers={}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Creates the {@link KafkaTemplate} used by {@code EventPublishAuthService}
     * to send auth-domain events.
     *
     * @return {@link KafkaTemplate} for String key/value messages
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
