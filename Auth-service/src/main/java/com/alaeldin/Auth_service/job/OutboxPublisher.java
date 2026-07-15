package com.alaeldin.Auth_service.job;

import com.alaeldin.Auth_service.model.OutboxEvent;
import com.alaeldin.Auth_service.service.OutBoxService;
import com.alaeldin.Auth_service.util.KafkaErrorClassifier;
import com.alaeldin.Auth_service.util.TopicResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutBoxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TopicResolver topicResolver;

    @Value("${app.outbox.batch-size:20}")
    private int batchSize;

    @Value("${app.outbox.publish-timeout-seconds:30}")
    private int publishTimeoutSeconds;

    /** Events stuck in PROCESSING longer than this are recovered back to PENDING. */
    @Value("${app.outbox.stale-threshold-minutes:5}")
    private int staleThresholdMinutes;

    // ─────────────────────────────────────────────────────────────
    //  Scheduled polling loops
    // ─────────────────────────────────────────────────────────────

    /**
     * Polls the outbox table on a fixed delay and publishes any pending events
     * to Kafka. Database-level {@code SELECT FOR UPDATE SKIP LOCKED} inside
     * {@link OutBoxService#lockBatchForPublishing} prevents concurrent workers
     * from processing the same event.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:10000}")
    public void publishOutboxEvents() {
        try {
            List<OutboxEvent> events = outboxService.lockBatchForPublishing(batchSize);
            if (CollectionUtils.isEmpty(events)) {
                log.debug("No pending outbox events");
                return;
            }
            log.info("Processing {} outbox event(s)", events.size());
            events.forEach(this::publishSingleEvent);
        } catch (Exception ex) {
            log.error("Outbox polling cycle failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Periodically recovers events that have been stuck in PROCESSING state,
     * e.g. due to a previous worker crash that did not mark the event as failed.
     * Runs on a slower cadence than the main publish loop.
     */
    @Scheduled(fixedDelayString = "${app.outbox.stale-recovery-interval-ms:60000}")
    public void recoverStaleEvents() {
        try {
            List<OutboxEvent> recovered = outboxService.findAndRecoverStaleEvents(staleThresholdMinutes);
            if (!recovered.isEmpty()) {
                log.info("Recovered {} stale outbox event(s) back to PENDING", recovered.size());
            }
        } catch (Exception ex) {
            log.error("Stale-event recovery cycle failed: {}", ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Per-event publishing
    // ─────────────────────────────────────────────────────────────

    private void publishSingleEvent(OutboxEvent event) {
        String payload = resolvePayload(event);
        String topic   = topicResolver.resolve(event.getAggregateType());

        log.debug("Publishing event: id={}, aggregateId={}, type={}, topic={}, attempt={}/{}",
                event.getId(), event.getAggregateId(), event.getEventType(),
                topic, event.getRetryCount() + 1, event.getMaxRetries());

        try {
            kafkaTemplate.send(topic, event.getAggregateId(), payload)
                    .orTimeout(publishTimeoutSeconds, TimeUnit.SECONDS)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            onSuccess(event, topic, result);
                        } else {
                            onFailure(event, topic, ex);
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to enqueue event id={}: {}", event.getId(), ex.getMessage(), ex);
            outboxService.markEventAsFailed(event.getId(), ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Completion callbacks
    // ─────────────────────────────────────────────────────────────

    private void onSuccess(OutboxEvent event, String topic, SendResult<String, String> result) {
        try {
            outboxService.markEventAsPublished(event.getId(), event.getIdempotencyKey());
            log.info("Published event: id={}, topic={}, partition={}, offset={}",
                    event.getId(), topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception ex) {
            log.error("Failed to mark event id={} as published: {}", event.getId(), ex.getMessage(), ex);
        }
    }

    private void onFailure(OutboxEvent event, String topic, Throwable ex) {
        String errorMsg  = ex.getMessage();
        String rootCause = KafkaErrorClassifier.rootMessage(ex);

        try {
            outboxService.markEventAsFailed(event.getId(), errorMsg);
        } catch (Exception markEx) {
            log.error("Failed to mark event id={} as failed: {}", event.getId(), markEx.getMessage(), markEx);
        }

        log.error("Kafka send failed: id={}, aggregateId={}, type={}, topic={}, error={}",
                event.getId(), event.getAggregateId(), event.getEventType(), topic, errorMsg);

        // Pass the Throwable directly — KafkaErrorClassifier.classify(Throwable) handles all
        // instanceof checks (TimeoutException, JsonProcessingException) and message inspection
        switch (KafkaErrorClassifier.classify(ex)) {
            case CONNECTIVITY  -> log.error("  Diagnosis: Kafka unreachable — verify broker at bootstrap server");
            case TIMEOUT       -> log.error("  Diagnosis: Timeout — consider raising publish-timeout-seconds (root: {})", rootCause);
            case SERIALIZATION -> log.error("  Diagnosis: Serialization error — check event payload format (root: {})", rootCause);
            default            -> log.error("  Root cause: {}", rootCause);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the event payload, or a safe {@code "{}"} sentinel if it is blank.
     * Uses {@link String#isBlank()} rather than {@link String#isEmpty()} so that
     * whitespace-only payloads are also treated as missing.
     */
    private String resolvePayload(OutboxEvent event) {
        String payload = event.getEventPayload();
        if (payload == null || payload.isBlank()) {
            log.warn("Empty payload for event id={} — using '{}'", event.getId(), "{}");
            return "{}";
        }
        return payload;
    }
}

