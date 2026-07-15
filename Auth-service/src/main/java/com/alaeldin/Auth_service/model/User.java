package com.alaeldin.Auth_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity representing an authenticated user in the Auth Service.
 *
 * <p>A {@code User} holds credentials, account-lock state, and the set of
 * {@link Role}s that determine their permissions across the platform.
 * Roles are resolved lazily and loaded only when the security context
 * explicitly needs them (e.g. during JWT generation).</p>
 *
 * <p>Account locking is time-based: after {@code MAX_FAILED_ATTEMPTS} consecutive
 * failed logins the account is locked until {@link #lockedUntil}. Call
 * {@link #isAccountLocked()} before granting access.</p>
 *
 * <p>Optimistic locking via {@link #version} prevents silent concurrent overwrites
 * when two requests modify the same user simultaneously (e.g. role assignment
 * racing with a password change).</p>
 *
 * @see Role
 * @see RefreshToken
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "users",
        indexes = {
                // Both columns are queried on every login — named indexes allow Flyway to manage them
                @Index(name = "idx_user_email",    columnList = "email",    unique = true)
        }
)
public class User {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * First Name From Users.
     */
    @Column(nullable = false, unique = true, length = 50)
    private String firstName;
    /**
     *  Middle Name From Users
     */
    @Column(length = 50)
    private String middleName;

    /**
     * Last Name For Users
     */
    @Column(nullable = false , length = 50)
     private String lastName;

    /**
     * Unique email address
     * Max 100 characters.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * Unique phone Number
     * Max 50 Characters
     */
    @Column(nullable = false, unique = true, length = 50)
    private String phone;

    /**
     *  Nationals Id From User Open Account is Necessary
     */
    @Column(nullable = false , unique = true , length = 100)
    private String  nationalId;

    /**
     * This Location Path File Like NationalId, Passport
     */
    @Column(nullable = false, unique = true, length = 100)
    private String identityFilePath;
    /**
     * Hashed password. Never store plain-text passwords.
     *
     * <p>BCrypt produces 60-character hashes; Argon2/SCrypt can exceed 255 characters.
     * {@code columnDefinition} is explicit here to document the intended maximum and
     * prevent any future ORM default from silently truncating longer hashes.</p>
     */
    @Column(name = "password_hash", nullable = false, columnDefinition = "VARCHAR(255)")
    private String passwordHash;

    /**
     * Whether this user account is active and allowed to authenticate.
     * Defaults to {@code true} on account creation. Set to {@code false}
     * to soft-delete or administratively disable the account.
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean isActive = true;

    /**
     * Running count of consecutive failed login attempts since the last
     * successful login. Reset to {@code 0} on success.
     * When this reaches the configured threshold the account is locked.
     */
    @Builder.Default
    @Column(name = "failed_login_attempts", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int failedLoginAttempts = 0;

    /**
     * UTC timestamp until which this account is locked.
     * {@code null} means the account is not currently locked.
     * Compared against UTC now in {@link #isAccountLocked()}.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * The set of roles granted to this user.
     *
     * <p>Loaded <strong>lazily</strong> — roles are only fetched from the database
     * when explicitly accessed (e.g. during JWT token generation), preventing
     * unnecessary joins on every user lookup.</p>
     *
     * <p>{@code @Builder.Default} ensures the builder initialises this to an
     * empty {@link HashSet} rather than {@code null}.</p>
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns        = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /**
     * The set of active refresh tokens issued to this user.
     *
     * <p>Cascade ALL + orphanRemoval ensures tokens are deleted automatically
     * when the user is removed or when a token is detached from the collection.</p>
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<RefreshToken> refreshTokens = new HashSet<>();

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
     * Timestamp when this user record was first created.
     * Set automatically by Hibernate on INSERT and never modified thereafter.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this user record.
     * Managed automatically by Hibernate on every UPDATE — no manual assignment needed.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────
    //  Business methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this account is currently locked.
     *
     * <p>The check is performed against UTC time to ensure consistent behaviour
     * across all nodes in a distributed deployment regardless of JVM timezone.</p>
     *
     * @return {@code true} if {@link #lockedUntil} is set and is after UTC now
     */
    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }
}
