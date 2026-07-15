package com.alaeldin.Auth_service.model;

import com.alaeldin.Auth_service.constant.Action;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity representing a single permission in the RBAC (Role-Based Access Control) system.
 *
 * <p>A {@code Permission} is the atomic combination of an {@link Action} (what can be done)
 * and a {@link Resource} (what it can be done to). For example: {@code READ} on {@code ACCOUNT}
 * grants read-only access to the accounts resource.</p>
 *
 * <p>Permissions are assigned to {@link Role}s via the {@code role_permissions} join table
 * managed by {@link Role}. A user inherits every permission of every role they hold.</p>
 *
 * <p>The combination of {@code resource} + {@code action} is enforced as unique at the
 * database level — the same grant cannot be registered twice.</p>
 *
 * <p>Optimistic locking via {@link #version} prevents silent concurrent overwrites when
 * two admin operations modify the same permission simultaneously.</p>
 *
 * @see Role
 * @see Resource
 * @see Action
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "permissions",
        uniqueConstraints = {
                // One row per action+resource pair — the same grant cannot exist twice
                @UniqueConstraint(
                        name  = "uq_permission_resource_action",
                        columnNames = {"resource_id", "action"}
                )
        },
        indexes = {
                @Index(name = "idx_permission_resource_action",
                        columnList = "resource_id, action")
        }
)
public class Permissions {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The resource this permission applies to (e.g. the {@code ACCOUNT} resource).
     *
     * <p>Loaded lazily — the full {@link Resource} object is only fetched when accessed,
     * avoiding unnecessary joins on every permission lookup.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    /**
     * The operation this permission grants (e.g. {@link Action#READ}, {@link Action#WRITE}).
     *
     * <p>Stored as a {@code STRING} so that re-ordering or inserting new enum constants
     * never corrupts existing rows — {@code ORDINAL} would silently break on any enum change.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Action action;

    /**
     * Version field for optimistic locking.
     * JPA increments this on every UPDATE; a stale-write attempt throws
     * {@link jakarta.persistence.OptimisticLockException} instead of silently overwriting data.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Timestamp when this permission was first created.
     * Set automatically by Hibernate on INSERT and never modified thereafter.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this permission record.
     * Managed automatically by Hibernate on every UPDATE — no manual assignment needed.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────
    //  Business methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns a compact string key that uniquely identifies this permission.
     *
     * <p>Format: {@code "<RESOURCE_NAME>:<ACTION>"} — e.g. {@code "ACCOUNT:READ"}.
     * Useful as a cache key in Redis or as the authority string passed to Spring
     * Security's {@code hasAuthority()} checks.</p>
     *
     * @return the permission key string, or {@code "UNKNOWN:UNKNOWN"} if either
     *         field is not yet initialised (e.g. before the entity is persisted)
     */
    public String toPermissionKey() {
        if (resource == null || resource.getName() == null || action == null) {
            return "UNKNOWN:UNKNOWN";
        }
        return resource.getName().name() + ":" + action.name();
    }
}
