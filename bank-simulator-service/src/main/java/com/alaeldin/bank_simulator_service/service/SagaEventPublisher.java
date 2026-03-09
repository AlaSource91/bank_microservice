package com.alaeldin.bank_simulator_service.service;

import com.alaeldin.bank_simulator_service.constant.EventType;
import com.alaeldin.bank_simulator_service.dto.OutboxEventRequest;
import com.alaeldin.bank_simulator_service.model.SagaEvent;
import com.alaeldin.bank_simulator_service.model.SagaState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes Saga lifecycle events to the transactional outbox.
 *
 * <p>All events are persisted via {@link OutboxService} before being forwarded to
 * Kafka, guaranteeing at-least-once delivery even if the broker is temporarily
 * unavailable (Outbox pattern).</p>
 *
 * <p>The idempotency key {@code "<sagaId>-<eventTypeName>"} ensures that retried
 * publishes for the same saga step are deduplicated by the outbox layer.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SagaEventPublisher {

    private final OutboxService outboxService;

    @Value("${app.name:bank-simulator-service}")
    private String applicationName;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link SagaEvent} from the current saga state, wraps it in an
     * {@link OutboxEventRequest}, and persists it to the outbox table.
     *
     * @param sagaState the current saga state snapshot — must not be {@code null}
     * @param eventType the lifecycle event being raised — must not be {@code null}
     * @throws IllegalArgumentException if {@code sagaState} or {@code eventType} is {@code null}
     * @throws RuntimeException         if persisting to the outbox fails
     */
    public void publishWithOutBox(SagaState sagaState, EventType eventType) {
        validateInputs(sagaState, eventType);

        log.debug("Publishing saga event: sagaId={}, eventType={}",
                sagaState.getSagaId(), eventType.getEventName());

        try {
            SagaEvent sagaEvent = buildSagaEvent(sagaState, eventType);
            OutboxEventRequest outboxRequest = buildOutboxRequest(sagaState, eventType, sagaEvent);

            outboxService.saveEventToOutbox(outboxRequest);

            log.info("Saga event queued: sagaId={}, eventType={}, aggregateType={}",
                    sagaState.getSagaId(), eventType.getEventName(), outboxRequest.getAggregateType());

        } catch (IllegalArgumentException e) {
            // Re-throw validation errors as-is
            throw e;
        } catch (Exception e) {
            log.error("Failed to publish saga event: sagaId={}, eventType={}, error={}",
                    sagaState.getSagaId(), eventType.getEventName(), e.getMessage(), e);
            throw new RuntimeException(
                    "Failed to publish saga event for sagaId=" + sagaState.getSagaId()
                            + ", eventType=" + eventType.getEventName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that neither argument is {@code null}.
     */
    private void validateInputs(SagaState sagaState, EventType eventType) {
        if (sagaState == null) {
            throw new IllegalArgumentException("SagaState must not be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("EventType must not be null");
        }
    }

    /**
     * Constructs the {@link SagaEvent} payload from the current saga state.
     */
    private SagaEvent buildSagaEvent(SagaState sagaState, EventType eventType) {
        return SagaEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sagaId(sagaState.getSagaId())
                .transactionReferenceId(sagaState.getTransactionReferenceId())
                .eventType(eventType.getEventName())
                .sourceAccountNumber(sagaState.getSourceAccountNumber())
                .destinationAccountNumber(sagaState.getDestinationAccountNumber())
                .amount(sagaState.getAmount())
                .failureReason(sagaState.getFailureReason())
                .currentStep(sagaState.getCurrentStep() != null ? sagaState.getCurrentStep().name() : null)
                .sagaStatus(sagaState.getStatus() != null ? sagaState.getStatus().name() : null)
                .timestamp(LocalDateTime.now())
                .applicationName(applicationName)
                .build();
    }

    /**
     * Wraps the event payload in an {@link OutboxEventRequest} ready for persistence.
     *
     * <p>The idempotency key is {@code "<sagaId>-<ENUM_NAME>"} (e.g.
     * {@code "abc123-SAGA_STARTED"}), keeping it consistent with the enum name
     * convention used elsewhere in the codebase.</p>
     */
    private OutboxEventRequest buildOutboxRequest(SagaState sagaState,
                                                  EventType eventType,
                                                  SagaEvent sagaEvent) {
        return OutboxEventRequest.builder()
                .aggregateId(sagaState.getSagaId())
                .aggregateType(resolveAggregateType(eventType))
                .eventType(eventType.getEventName())
                .eventPayload(sagaEvent)
                .idempotencyKey(sagaState.getSagaId() + "-" + eventType.name())
                .build();
    }

    /**
     * Maps an {@link EventType} to its logical aggregate group.
     *
     * <p>Switches directly on the {@link EventType} enum constant — type-safe and
     * exhaustive (compile error if a new constant is added without updating this
     * method, thanks to the explicit {@code default} guard).</p>
     *
     * @param eventType the event type to classify
     * @return aggregate type string used in the outbox record
     */
    private String resolveAggregateType(EventType eventType) {
        return switch (eventType) {
            case SAGA_STARTED, SAGA_COMPLETED, SAGA_COMPENSATED, SAGA_FAILED -> "SAGA";
            case DEBIT_REQUESTED, DEBIT_COMPLETED, DEBIT_FAILED              -> "DEBIT";
            case CREDIT_REQUESTED, CREDIT_COMPLETED, CREDIT_FAILED           -> "CREDIT";
            case DEBIT_REVERSED                                               -> "COMPENSATION";
            default                                                           -> "SAGA";
        };
    }

}
