package com.alaeldin.Auth_service.dto;

import com.alaeldin.Auth_service.constant.ResourceName;
import com.alaeldin.Auth_service.model.Resource;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a protected {@link Resource} returned to API clients.
 *
 * <p>Exposes only the fields relevant to consumers — JPA internals
 * ({@code version}) are intentionally omitted.</p>
 *
 * <p>Use {@link #fromResource(Resource)} to build an instance from an entity.</p>
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceResponse {

    /** Database primary key of the resource record. */
    private Long id;

    /**
     * Logical identifier of the resource (e.g. {@code ACCOUNT}, {@code TRANSACTION}).
     * Matches the {@link ResourceName} enum constant stored in the database.
     */
    private ResourceName name;

    /**
     * Ant-style URL pattern this resource guards (e.g. {@code /api/accounts/**}).
     * Derived from {@link ResourceName#getApiPath()}.
     */
    private String apiPath;

    /** Human-readable explanation of what this resource covers. */
    private String description;

    /** UTC timestamp when this resource record was first persisted. */
    private LocalDateTime createdAt;

    /** UTC timestamp of the most recent update to this resource record. */
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────
    //  Factory
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a {@code ResourceResponse} from a {@link Resource} entity.
     *
     * @param resource the source entity; must not be {@code null}
     * @return a fully populated response DTO
     */
    public static ResourceResponse fromResource(Resource resource) {
        return ResourceResponse.builder()
                .id(resource.getId())
                .name(resource.getName())
                .apiPath(resource.getName() != null ? resource.getName().getApiPath() : null)
                .description(resource.getDescription())
                .createdAt(resource.getCreatedAt())
                .updatedAt(resource.getUpdatedAt())
                .build();
    }
}
