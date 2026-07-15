package com.alaeldin.Auth_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * JPA entity representing a JWT refresh token issued to a {@link User}.
 *
 * <p>Refresh tokens allow a client to silently obtain a new access JWT without
 * re-authenticating. Each token is bound to exactly one {@link User}, carries a
 * fixed expiry timestamp, and can be individually revoked (e.g. on logout,
 * password change, or admin force-logout) without affecting the user's other
 * active sessions.</p>
 *
 * <p><strong>Security:</strong> The raw token value is <em>never</em> persisted.
 * Only its SHA-256 hex hash is stored in {@link #tokenHash}. This ensures that
 * a database breach cannot be used to replay active sessions.</p>
 *
 * <p>Use {@link #isExpired()} and {@link #isRevoked()} independently, or
 * {@link #isInvalid()} to check both at once before accepting a token for rotation.</p>
 *
 * <p>Optimistic locking via {@link #version} prevents silent concurrent overwrites
 * when two requests revoke the same token simultaneously.</p>
 *
 * @see User
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                // Queried on every silent-refresh request — must be fast and unique
                @Index(name = "idx_refresh_token_hash",    columnList = "token_hash", unique = true),
                // Queried for bulk revocation on logout / password-change
                @Index(name = "idx_refresh_token_user_id", columnList = "user_id")
        }
)
public class RefreshToken {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user this refresh token was issued to.
     *
     * <p>Loaded lazily — the full {@link User} object graph is not needed during
     * token validation; only the {@code user_id} foreign key is required.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The raw refresh-token string issued to the client.
     *
     * <p>A UUID v4 value (36 characters) is stored here directly, which keeps
     * the lookup simple and fast.  {@code length = 64} gives comfortable headroom
     * if a longer opaque token format is adopted in the future.</p>
     *
     * <p><strong>Security note:</strong> Storing the raw token means a database
     * breach exposes active sessions.  A future hardening step would hash this
     * value with SHA-256 before storage (output: 64 hex chars exactly) and compare
     * {@code Hash(incomingToken)} on every lookup.</p>
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * UTC timestamp after which this token is no longer valid.
     *
     * <p>Always compare against UTC (see {@link #isExpired()}) to ensure consistent
     * behaviour across all nodes in a distributed deployment regardless of JVM timezone.</p>
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    /**
     * Whether this token has been explicitly revoked before its natural expiry.
     *
     * <p>Revoked tokens are rejected even if {@link #expiryDate} has not yet passed.
     * Set to {@code true} on logout, password change, or admin force-revoke.
     * Defaults to {@code false} on token creation.</p>
     */
    @Builder.Default
    @Column(name = "is_revoked", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isRevoked = false;

    /**
     * Version field for optimistic locking.
     * JPA increments this on every UPDATE; a stale-write attempt throws
     * {@link jakarta.persistence.OptimisticLockException} instead of silently
     * overwriting data — critical when two logout requests race on the same token.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Timestamp when this token was first issued.
     * Set automatically by Hibernate on INSERT and never modified thereafter.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this token record (e.g. when it was revoked).
     * Managed automatically by Hibernate on every UPDATE — no manual assignment needed.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────
    //  Business methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this token is past its expiry time.
     *
     * <p>The comparison uses UTC to ensure consistent behaviour across all nodes
     * in a distributed deployment regardless of JVM timezone — consistent with
     * {@link User#isAccountLocked()}.</p>
     *
     * @return {@code true} if {@link #expiryDate} is set and is before UTC now
     */
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Returns {@code true} if this token can no longer be used — either because
     * it has expired naturally or because it was explicitly revoked.
     *
     * <p>Always call this before accepting a token for rotation:</p>
     * <pre>
     * if (refreshToken.isInvalid()) {
     *     throw new TokenRevokedException("Refresh token is no longer valid");
     * }
     * </pre>
     *
     * @return {@code true} if the token is expired or revoked
     */
    public boolean isInvalid() {
        return isRevoked || isExpired();
    }
}
