package com.alaeldin.Auth_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Immutable value object used to pass outbox-event data from the service layer
 * to {@link com.alaeldin.Auth_service.service.OutBoxService}.
 *
 * <p>{@code @Data} is intentionally avoided here — as a DTO this object should
 * only be readable after construction, so only {@code @Getter} is exposed.
 * Mutation after creation is not needed and would be a code smell.</p>
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OutboxEventRequest {

    /** Identifier of the domain aggregate that produced this event (e.g. the user's ID). */
    private String aggregateId;

    /** Logical type of the aggregate (e.g. {@code "AUTH_SECURITY"}). */
    private String aggregateType;

    /** Discriminator for the event kind (e.g. {@code "USER_REGISTERED"}). */
    private String eventType;

    /**
     * Typed event payload — serialised to JSON by {@code OutBoxService} before storage.
     * Keeping it as {@link Object} allows any domain event class to be passed in.
     */
    private Object eventPayload;

    /** Unique key that prevents the same logical event from being inserted twice. */
    private String idempotencyKey;
}
