package com.alaeldin.Auth_service.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Enum representing the set of actions (permissions) that can be granted to a role
 * in the Auth Service RBAC (Role-Based Access Control) system.
 *
 * <p>Each constant carries a human-readable label, a short description of the
 * operation it permits, and the HTTP methods it maps to — making it easy to wire
 * directly into Spring Security's {@code requestMatchers} and audit logs without
 * scattering magic strings across the codebase.</p>
 *
 * <p>Usage examples:</p>
 * <pre>
 * // Spring Security config
 * .requestMatchers(HttpMethod.GET, "/api/accounts/**")
 *     .hasAuthority(Action.READ.name())
 *
 * // Audit log
 * log.info("User performed action: {} on resource: {}",
 *     Action.WRITE.getDisplayName(),
 *     ResourceName.ACCOUNT.getDisplayName());
 * </pre>
 *
 * @see ResourceName
 */
@Getter
public enum Action {

    /**
     * Read-only access — view existing data without modifying it.
     * Maps to HTTP {@code GET} and {@code HEAD}.
     */
    READ(
            "Read",
            "View and retrieve existing records",
            List.of("GET", "HEAD")
    ),

    /**
     * Write (create) access — submit new data to the system.
     * Maps to HTTP {@code POST}.
     */
    WRITE(
            "Write",
            "Create and submit new records",
            List.of("POST")
    ),

    /**
     * Update access — modify existing data.
     * Maps to HTTP {@code PUT} and {@code PATCH}.
     */
    UPDATE(
            "Update",
            "Modify and update existing records",
            List.of("PUT", "PATCH")
    ),

    /**
     * Delete access — permanently remove data from the system.
     * Maps to HTTP {@code DELETE}.
     */
    DELETE(
            "Delete",
            "Permanently remove existing records",
            List.of("DELETE")
    ),

    /**
     * Execute access — trigger operations or jobs (e.g. token revocation,
     * force-logout, saga compensation). Maps to HTTP {@code POST}.
     */
    EXECUTE(
            "Execute",
            "Trigger operational commands and background jobs",
            List.of("POST")
    );

    // ─────────────────────────────────────────────────────────────
    //  Fields
    // ─────────────────────────────────────────────────────────────

    /** Human-readable label for UI display and log messages (e.g. {@code "Read"}). */
    private final String displayName;

    /** Short description of what this action permits. */
    private final String description;

    /**
     * HTTP methods that this action corresponds to.
     * Used when mapping RBAC permissions to Spring Security request matchers.
     */
    private final List<String> httpMethods;

    // ─────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────

    Action(String displayName, String description, List<String> httpMethods) {
        this.displayName = displayName;
        this.description = description;
        this.httpMethods = httpMethods;
    }

    // ─────────────────────────────────────────────────────────────
    //  Utility methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Looks up an {@code Action} by its {@link #displayName} (case-insensitive).
     *
     * <p>Useful when deserializing display labels from API requests or config files
     * without relying on the raw enum constant name.</p>
     *
     * @param displayName the human-readable label to search for (e.g. {@code "Read"})
     * @return an {@link Optional} containing the matching {@code Action},
     *         or {@link Optional#empty()} if none matches
     */
    public static Optional<Action> fromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(a -> a.displayName.equalsIgnoreCase(displayName.trim()))
                .findFirst();
    }

    /**
     * Returns the {@link #displayName} — convenient for logging and error messages.
     *
     * @return human-readable action label (e.g. {@code "Read"})
     */
    @Override
    public String toString() {
        return displayName;
    }
}
