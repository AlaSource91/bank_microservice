package com.alaeldin.Auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Request payload for the role-assignment endpoint
 * ({@code POST /api/v1/auth/admin/users/{userId}/roles}).
 *
 * <p>Validation constraints are aligned with the
 * {@link com.alaeldin.Auth_service.model.Role} entity so that obviously
 * invalid inputs are rejected at the API boundary — before any database
 * lookup is performed.</p>
 *
 * <p>This class is intentionally <strong>immutable</strong> — no setters are
 * exposed. Jackson deserializes it via the Lombok {@code @Builder} (enabled by
 * {@code @Jacksonized}).</p>
 *
 * @see com.alaeldin.Auth_service.model.Role
 * @see com.alaeldin.Auth_service.model.User
 */
@Getter
@Builder
@Jacksonized
public class AssignRoleRequest {

    /**
     * The primary key of the user to whom the role will be assigned.
     *
     * <ul>
     *   <li>Must not be {@code null}.</li>
     *   <li>Must be a positive integer — auto-generated IDs are always {@code > 0}.</li>
     * </ul>
     */
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be a positive number")
    private Long userId;

    /**
     * The unique name of the role to assign (e.g. {@code "ADMIN"}, {@code "USER"}).
     *
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must not exceed 50 characters (matches {@code roles.name} column length).</li>
     *   <li>Only uppercase letters, digits, and underscores — consistent with the
     *       naming convention enforced at role-creation time.</li>
     * </ul>
     */
    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    @Pattern(
            regexp  = "^[A-Z0-9_]+$",
            message = "Role name must contain only uppercase letters, digits, and underscores"
    )
    private String roleName;
}


