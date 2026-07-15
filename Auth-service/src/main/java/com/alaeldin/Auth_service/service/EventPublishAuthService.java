package com.alaeldin.Auth_service.service;

import com.alaeldin.Auth_service.constant.AuthEventType;
import com.alaeldin.Auth_service.dto.OutboxEventRequest;
import com.alaeldin.Auth_service.model.AuthEvent;
import com.alaeldin.Auth_service.model.Role;
import com.alaeldin.Auth_service.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventPublishAuthService {

    private final OutBoxService outboxService;

    private static final String AGGREGATE_TYPE = "AUTH_SECURITY";

    /**
     * Validates inputs, builds an {@link AuthEvent} populated from the given {@link User},
     * wraps it in an {@link OutboxEventRequest} and persists it to the outbox table within
     * the caller's transaction.
     *
     * @param user          the user that triggered the event — must not be {@code null}
     * @param authEventType the type of auth event — must not be {@code null}
     * @throws IllegalArgumentException if any required field is missing
     */
    @Transactional
    public void saveAuthEventOutBox(User user, AuthEventType authEventType) {
        validateInputs(user, authEventType);

        String email = user.getEmail();
        log.debug("Processing auth event: email={}, eventType={}", email, authEventType);

        OutboxEventRequest outboxEventRequest = createOutboxEventRequest(user, authEventType);
        outboxService.saveEventToOutbox(outboxEventRequest);

        log.info("Auth event saved to outbox: email={}, eventType={}", email, authEventType);
    }

    // ─────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds a fully-populated {@link OutboxEventRequest} from the given user and event type.
     * The {@code aggregateId} is set to the user's database ID so that the outbox record is
     * naturally linked to the user aggregate, rather than an arbitrary random UUID.
     * The {@code AuthEvent} payload carries the user's ID, resolved roles, and event type.
     */
    private OutboxEventRequest createOutboxEventRequest(User user, AuthEventType authEventType) {

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        AuthEvent authEvent = AuthEvent.builder()
                .eventType(authEventType)
                .userId(user.getId())
                .roles(roleNames)
                .build();

        return OutboxEventRequest.builder()
                .aggregateId(String.valueOf(user.getId()))
                .aggregateType(AGGREGATE_TYPE)
                .eventType(authEventType.name())
                .eventPayload(authEvent)
                .idempotencyKey(buildIdempotencyKey(user, authEventType))
                .build();
    }

    /**
     * Produces a deterministic idempotency key that uniquely identifies this
     * (user, event-type, version) triple so that duplicate outbox inserts are
     * rejected by the unique constraint on the {@code idempotency_key} column.
     *
     * <p>Format: {@code <email>:<EVENT_TYPE>:v<version>}</p>
     */
    private String buildIdempotencyKey(User user, AuthEventType authEventType) {
        return user.getId()
                + ":" + authEventType.name()
                + ":v" + user.getVersion();
    }

    /**
     * Guards against null / blank required inputs.
     * Note: {@code email} and {@code username} checks use {@code ||} so that a
     * {@code null} value is caught <em>before</em> calling {@code trim()}.
     */
    private void validateInputs(User user, AuthEventType authEventType) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (authEventType == null) {
            throw new IllegalArgumentException("Auth event type cannot be null");
        }
        if (!StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("User email is required");
        }

    }
}
