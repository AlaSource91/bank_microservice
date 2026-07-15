package com.alaeldin.Auth_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity representing a role in the RBAC (Role-Based Access Control) system.
 *
 * <p>A {@code Role} is a named collection of {@link Permissions} that can be
 * assigned to users. A user inherits every permission belonging to each role
 * they hold. Typical roles include {@code ADMIN}, {@code USER}, and
 * {@code AUDITOR}.</p>
 *
 * <p>The relationship to {@link Permissions} is many-to-many: one role can
 * hold many permissions, and the same permission can belong to many roles.
 * The join is persisted in the {@code role_permissions} table.</p>
 *
 * <p>Optimistic locking via {@link #version} prevents silent concurrent
 * overwrites when two admin operations modify the same role simultaneously.</p>
 *
 * @see Permissions
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "roles",
        indexes = {
                // Roles are looked up by name on every authentication — must be fast
                @Index(name = "idx_role_name", columnList = "name", unique = true)
        }
)
public class Role {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique role identifier used throughout the application and stored in JWTs
     * (e.g. {@code "ADMIN"}, {@code "USER"}, {@code "AUDITOR"}).
     * Must be unique across all rows.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * Optional human-readable description of what this role permits.
     * Stored as TEXT to support detailed role documentation.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The set of permissions granted to this role.
     *
     * <p>Loaded lazily — permissions are only fetched from the database when
     * explicitly accessed, avoiding unnecessary joins on every role lookup.</p>
     *
     * <p>{@code @Builder.Default} ensures Lombok's builder initialises this to
     * an empty {@link HashSet} instead of {@code null}.</p>
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns        = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permissions> permissions = new HashSet<>();

    /**
     * Version field for optimistic locking.
     * JPA increments this on every UPDATE; a stale-write attempt throws
     * {@link jakarta.persistence.OptimisticLockException} instead of silently
     * overwriting data.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Timestamp when this role was first created.
     * Set automatically by Hibernate on INSERT and never modified thereafter.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this role record.
     * Managed automatically by Hibernate on every UPDATE — no manual assignment needed.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
