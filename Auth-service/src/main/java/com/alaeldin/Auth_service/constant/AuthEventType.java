package com.alaeldin.Auth_service.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum representing every domain event that the Auth Service can publish to Kafka.
 *
 * <p>Each constant carries a human-readable label, a description of when the event
 * is emitted, and the Kafka topic it is published to — eliminating magic strings from
 * producers, consumers, and audit logs across the whole microservice platform.</p>
 *
 * <p>Usage examples:</p>
 * <pre>
 * // Publishing an event
 * kafkaTemplate.send(AuthEventType.USER_LOGIN.getKafkaTopic(), payload);
 *
 * // Audit log
 * log.info("Auth event fired: {}", AuthEventType.USER_REGISTERED.getDisplayName());
 *
 * // Resolving from a raw topic string
 * AuthEventType.fromTopic("auth.user.login").ifPresent(e -> process(e));
 * </pre>
 *
 * @see Action
 * @see ResourceName
 */
@Getter
public enum AuthEventType {

    /**
     * Fired when a new user successfully completes the registration flow.
     * Triggers downstream onboarding workflows (e.g. welcome email, account creation).
     */
    USER_REGISTERED(
            "User Registered",
            "A new user account was successfully created",
            "auth.events"
    ),

    /**
     * Fired when a user's information is updated by an admin.
     * Downstream services should refresh user data and cached information.
     */
    USER_UPDATED(
            "User Updated",
            "A user's information was updated by an admin",
            "auth.events"
    ),

    /**
     * Fired when a user authenticates successfully and receives a JWT.
     * Used for session tracking, anomaly detection, and audit trails.
     */
    USER_LOGIN(
            "User Login",
            "A user authenticated successfully and received a JWT",
            "auth.events"
    ),

    /**
     * Fired when a user explicitly terminates their session.
     * Triggers JWT blacklisting in Redis and session cleanup.
     */
    USER_LOGOUT(
            "User Logout",
            "A user explicitly logged out and their token was revoked",
            "auth.events"
    ),

    /**
     * Fired when a user successfully changes their password.
     * Triggers invalidation of all existing tokens for that user (force re-login).
     */
    PASSWORD_CHANGED(
            "Password Changed",
            "A user changed their password — all active tokens are invalidated",
            "auth.events"
    ),

    /**
     * Fired when an account is locked due to repeated failed login attempts
     * or an admin action. Downstream services should reject requests from this user.
     */
    ACCOUNT_LOCKED(
            "Account Locked",
            "A user account was locked due to failed attempts or admin action",
            "auth.events"
    ),

    /**
     * Fired when a client silently rotates their session using a refresh token.
     * A new access/refresh token pair is issued and the old refresh token is revoked.
     */
    TOKEN_REFRESHED(
            "Token Refreshed",
            "A user silently refreshed their session — refresh token was rotated",
            "auth.events"
    ),

    /**
     * Fired when a role is granted to a user.
     * Downstream services should refresh the user's cached permissions.
     */
    ROLE_ASSIGNED(
            "Role Assigned",
            "A role was granted to a user — permission cache should be refreshed",
            "auth.events"
    ),

    /**
     * Fired when a role is removed from a user.
     * Downstream services should refresh the user's cached permissions
     * and revoke any active tokens relying on that role.
     */
    ROLE_REVOKED(
            "Role Revoked",
            "A role was removed from a user — active tokens may need revocation",
            "auth.events"
    );

    // ─────────────────────────────────────────────────────────────
    //  Fields
    // ─────────────────────────────────────────────────────────────

    /** Human-readable label suitable for UI display and log messages. */
    private final String displayName;

    /** Short description of what triggers this event and its downstream impact. */
    private final String description;

    /**
     * Kafka topic this event is published to.
     * Follows the {@code auth.<entity>.<action>} naming convention.
     */
    private final String kafkaTopic;

    // ─────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────

    AuthEventType(String displayName, String description, String kafkaTopic) {
        this.displayName  = displayName;
        this.description  = description;
        this.kafkaTopic   = kafkaTopic;
    }

    // ─────────────────────────────────────────────────────────────
    //  Utility methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Resolves an {@code AuthEventType} by its exact Kafka topic string.
     *
     * <p>Useful in Kafka consumers that receive a raw topic name and need to
     * dispatch to the correct handler without a chain of {@code if/else} checks.</p>
     *
     * @param topic the Kafka topic string to look up (e.g. {@code "auth.user.login"})
     * @return an {@link Optional} containing the matching {@code AuthEventType},
     *         or {@link Optional#empty()} if none matches
     */
    public static Optional<AuthEventType> fromTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(e -> e.kafkaTopic.equalsIgnoreCase(topic.trim()))
                .findFirst();
    }

    /**
     * Returns the {@link #displayName} — convenient for logging and error messages.
     *
     * @return human-readable event label (e.g. {@code "User Login"})
     */
    @Override
    public String toString() {
        return displayName;
    }
}
