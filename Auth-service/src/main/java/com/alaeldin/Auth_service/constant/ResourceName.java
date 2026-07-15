package com.alaeldin.Auth_service.constant;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum representing all protected resources in the P2P microservice platform.
 *
 * <p>Each constant maps a logical resource name to its human-readable label,
 * the URL path pattern it guards, and a short description of its purpose.
 * Use this enum in security configurations, audit logs, and access-control checks
 * to avoid magic strings scattered across the codebase.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * // In a Spring Security config
 * .requestMatchers(ResourceName.ACCOUNT.getApiPath()).hasRole("USER")
 *
 * // In an audit log
 * log.info("Access granted to resource: {}", ResourceName.TRANSACTION.getDisplayName());
 * </pre>
 */
@Getter
public enum ResourceName {

    /**
     * Bank account operations.
     * Covers {@code /api/accounts/**}
     */
    ACCOUNT(
            "Bank Account",
            "/api/accounts/**",
            "Create, view, and manage bank accounts"
    ),

    /**
     * Money transfer / P2P transaction operations.
     * Covers {@code /api/transactions/**}
     */
    TRANSACTION(
            "Transaction",
            "/api/transactions/**",
            "Initiate and track peer-to-peer money transfers"
    ),

    /**
     * Authenticated user's own profile management.
     * Covers {@code /api/v1/auth/me}
     */
    USER_PROFILE(
            "User Profile",
            "/api/v1/auth/me",
            "View and update the currently authenticated user's profile"
    ),

    /**
     * Administrative panel — restricted to ADMIN role.
     * Covers {@code /api/v1/admin/**}
     */
    ADMIN_PANEL(
            "ADMIN_PANEL",
            "/api/v1/admin/**",
            "Administrative operations for platform management (ADMIN only)"
    ),

    /**
     * Reports and analytics dashboard.
     * Covers {@code /api/reports/**}
     */
    REPORT(
            "Report",
            "/api/reports/**",
            "Generate and view financial reports and analytics"
    ),

    /**
     * Immutable audit trail viewer.
     * Covers {@code /api/audit/**}
     */
    AUDIT_LOG(
            "Audit Log",
            "/api/audit/**",
            "View the immutable audit trail of all system events"
    );

    // ─────────────────────────────────────────────────────────────
    //  Fields
    // ─────────────────────────────────────────────────────────────

    /** Human-readable label suitable for UI display and log messages. */
    private final String displayName;

    /**
     * Ant-style URL pattern that this resource covers.
     * Matches the path patterns used in Spring Security's {@code requestMatchers()}.
     */
    private final String apiPath;

    /** Short description of what this resource represents. */
    private final String description;

    // ─────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────

    ResourceName(String displayName, String apiPath, String description) {
        this.displayName = displayName;
        this.apiPath     = apiPath;
        this.description = description;
    }

    // ─────────────────────────────────────────────────────────────
    //  Utility methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Resolves a {@code ResourceName} whose {@link #apiPath} prefix matches
     * the given request URI.
     *
     * <p>The match is prefix-based: the trailing {@code /**} wildcard is stripped
     * before comparison, so {@code /api/accounts/123} resolves to {@link #ACCOUNT}.</p>
     *
     * @param requestUri the incoming HTTP request URI (e.g. {@code /api/accounts/123})
     * @return an {@link Optional} containing the matching {@code ResourceName},
     *         or {@link Optional#empty()} if no match is found
     */
    public static Optional<ResourceName> fromPath(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(r -> {
                    String base = r.apiPath.endsWith("/**")
                            ? r.apiPath.substring(0, r.apiPath.length() - 3)
                            : r.apiPath;
                    return requestUri.startsWith(base);
                })
                .findFirst();
    }

    /**
     * Returns the {@link #displayName} — convenient for logging and error messages.
     *
     * @return human-readable resource name (e.g. {@code "Bank Account"})
     */
    @Override
    public String toString() {
        return displayName;
    }
}
