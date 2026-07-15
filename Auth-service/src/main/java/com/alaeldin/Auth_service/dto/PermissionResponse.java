package com.alaeldin.Auth_service.dto;

import com.alaeldin.Auth_service.constant.Action;
import com.alaeldin.Auth_service.model.Permissions;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a single {@link Permissions} record returned to API clients.
 *
 * <p>Exposes only the fields relevant to consumers — JPA internals
 * ({@code version}) are intentionally omitted. The resource is represented
 * as a {@link ResourceResponse} rather than exposing the raw entity.</p>
 *
 * <p>Use {@link #fromPermission(Permissions)} to build an instance from an entity.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionResponse {

    /** Database primary key of the permission record. */
    private Long id;

    /**
     * The resource this permission applies to.
     * Presented as a safe DTO projection — never the raw JPA entity.
     */
    private ResourceResponse resource;

    /**
     * The operation this permission grants (e.g. {@link Action#READ}, {@link Action#WRITE}).
     */
    private Action action;

    /**
     * Compact authority string used by Spring Security and Redis cache
     * (e.g. {@code "ACCOUNT:READ"}).
     */
    private String permissionKey;

    /** UTC timestamp when this permission record was first persisted. */
    private LocalDateTime createdAt;

    /** UTC timestamp of the most recent update to this permission record. */
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────
    //  Factory
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a {@code PermissionResponse} from a {@link Permissions} entity.
     *
     * <p>The entity's {@code resource} association must be initialised
     * (eagerly or within an open session) before calling this method.</p>
     *
     * @param permission the source entity; must not be {@code null}
     * @return a fully populated response DTO
     */
    public static PermissionResponse fromPermission(Permissions permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .resource(permission.getResource() != null
                        ? ResourceResponse.fromResource(permission.getResource())
                        : null)
                .action(permission.getAction())
                .permissionKey(permission.toPermissionKey())
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }
}
