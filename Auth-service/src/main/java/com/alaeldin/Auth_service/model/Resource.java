package com.alaeldin.Auth_service.model;

import com.alaeldin.Auth_service.constant.ResourceName;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity representing a protected resource in the RBAC (Role-Based Access Control) system.
 *
 * <p>A {@code Resource} is the target of a permission: it maps a logical
 * {@link ResourceName} (e.g. {@code ACCOUNT}, {@code TRANSACTION}) to a database
 * row that roles and permissions can reference via foreign key.</p>
 *
 * <p>Each resource name is enforced as unique — the same API resource cannot be
 * registered twice. Optimistic locking via {@link #version} prevents silent
 * concurrent overwrites.</p>
 *
 * @see ResourceName
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "resources",
        indexes = {
                // Fast lookup by name — used in permission checks on every request
                @Index(name = "idx_resource_name", columnList = "name", unique = true)
        }
)
public class Resource {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The logical identifier of this resource, mapped to the {@link ResourceName} enum.
     * Stored as its string constant name (e.g. {@code "ACCOUNT"}).
     * Must be unique across all rows — one row per resource type.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private ResourceName name;

    /**
     * Human-readable explanation of what this resource represents and
     * what operations it covers. Stored as TEXT to support long descriptions.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Version field for optimistic locking.
     * JPA automatically increments this on every update; a stale-write attempt
     * throws {@link jakarta.persistence.OptimisticLockException} rather than
     * silently overwriting data.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Timestamp when this resource record was first created.
     * Set automatically by Hibernate on INSERT and never modified thereafter.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this resource record.
     * Managed automatically by Hibernate on every UPDATE — no manual assignment needed.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
