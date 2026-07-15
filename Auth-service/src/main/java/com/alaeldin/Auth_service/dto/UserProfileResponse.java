package com.alaeldin.Auth_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.alaeldin.Auth_service.model.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response payload for profile queries — {@code GET /api/v1/auth/me} and
 * admin user-lookup endpoints.
 *
 * <p>This DTO is a <strong>safe projection</strong> of the {@link User} entity:
 * it exposes only the fields that are appropriate for the client to see.
 * Sensitive internals ({@code passwordHash}, {@code failedLoginAttempts},
 * {@code lockedUntil} raw value, {@code version}) are intentionally omitted.</p>
 *
 * <p>The class is intentionally <strong>immutable</strong> — no setters are
 * exposed. Service code constructs instances exclusively via the static
 * {@link #fromUser(User)} factory or the Lombok {@code @Builder}.
 * {@code @JsonInclude} suppresses {@code null} fields so that optional
 * claims are never serialised as {@code null} in the JSON body.</p>
 *
 * @see User
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    /**
     * Internal numeric identifier of the user.
     * Useful for client-side caching — never use as a trust anchor; always
     * validate the JWT signature server-side.
     */
    private Long userId;

    /**
     * Unique login handle of the user.
     * Matches {@link User#getFirstName()} ()}.
     */
    private String firstName;
    /**
     *  Middle Name
     */
    private String middleName;
    /**
     * Last Name
     */
    private String lastName;

    /**
     * Email address of the user.
     */
    private String email;
    /**
     * phone Of The User
     */
    private String phone;

    /**
     * Whether the account is currently active and allowed to authenticate.
     * {@code false} means the account has been administratively disabled.
     */
    private Boolean isActive;

    /**
     * Whether the account is temporarily locked due to brute-force protection.
     * {@code true} means the user must wait until {@link #lockedUntil} before
     * logging in again.
     */
    private Boolean isAccountLocked;

    /**
     * UTC timestamp until which the account is locked.
     * {@code null} when the account is not currently locked.
     */
    private LocalDateTime lockedUntil;

    /**
     * Set of role names granted to this user (e.g. {@code "ADMIN"}, {@code "USER"}).
     * Empty when the user holds no roles — never {@code null}.
     */
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    /**
     * Flat set of permission strings derived from the user's roles
     * (e.g. {@code "ACCOUNT:READ"}, {@code "TRANSACTION:EXECUTE"}).
     * Empty when no permissions are resolved — never {@code null}.
     */
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    /**
     * UTC timestamp when this user account was first created.
     */
    private LocalDateTime createdAt;

    /**
     * UTC timestamp of the most recent update to this user record.
     * Clients can use this to detect stale cached profiles.
     */
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────
    //  Factory
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a {@code UserProfileResponse} from a fully loaded {@link User} entity.
     *
     * <p>The {@code User} must have its {@code roles} and {@code roles.permissions}
     * associations initialised (either eagerly or within an open session) before
     * calling this method — otherwise Hibernate will throw a
     * {@code LazyInitializationException}.</p>
     *
     * @param user the fully loaded user entity; must not be {@code null}
     * @return a populated {@code UserProfileResponse}
     */
    public static UserProfileResponse fromUser(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toCollection(HashSet::new));

        Set<String> permissionKeys = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.toPermissionKey())
                .collect(Collectors.toCollection(HashSet::new));

        return UserProfileResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.isActive())
                .isAccountLocked(user.isAccountLocked())
                .lockedUntil(user.getLockedUntil())
                .roles(roleNames)
                .permissions(permissionKeys)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
