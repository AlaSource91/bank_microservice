package com.alaeldin.Auth_service.repository;

import com.alaeldin.Auth_service.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>All read operations are annotated with {@code @Transactional(readOnly = true)}
 * to allow Hibernate to skip dirty-checking and enable database-driver-level
 * read optimisations (e.g. replica routing).</p>
 *
 * <p>Write operations ({@code @Modifying}) run in their own transaction and
 * must be called from a {@code @Transactional} service method.</p>
 *
 * @see User
 */
@Repository
@Transactional(readOnly = true)
public interface UserRepository extends JpaRepository<User, Long> {

    // ─────────────────────────────────────────────────────────────
    //  Single-user lookups
    // ─────────────────────────────────────────────────────────────


    /**
     * Finds a user by their unique email address.
     *
     * <p>Used during password-reset and account-recovery flows.</p>
     *
     * @param email the email address to search for (case-insensitive at DB level)
     * @return an {@link Optional} containing the matching user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by their unique phone number.
     *
     * @param phone the phone number to search for
     * @return an {@link Optional} containing the matching user, or empty if not found
     */
    Optional<User> findByPhone(String phone);

    /**
     * Finds a user by their unique national ID.
     *
     * @param nationalId the national ID to search for
     * @return an {@link Optional} containing the matching user, or empty if not found
     */
    Optional<User> findByNationalId(String nationalId);

    /**
     * Finds a user by username and eagerly loads their full security graph
     * ({@code roles → permissions → resource}) in a single JOIN query.
     *
     * <p>Use this method <strong>only</strong> when building the Spring Security
     * {@code Authentication} object (i.e. during login and JWT generation). For all
     * other lookups use {@link #findByEmail(String)} (String)} to avoid unnecessary joins.</p>
     *
     * <p>The {@code @EntityGraph} overrides the default {@code LAZY} fetch strategy
     * for these associations, preventing N+1 queries when iterating granted authorities.</p>
     *
     * @param email the login handle to search for
     * @return an {@link Optional} containing the user with roles and permissions loaded,
     *         or empty if not found
     */
    @EntityGraph(attributePaths = {
            "roles",
            "roles.permissions",
            "roles.permissions.resource"
    })
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findWithRolesAndPermissionsByEmail(@Param("email") String email);

    // ─────────────────────────────────────────────────────────────
    //  Existence checks
    // ─────────────────────────────────────────────────────────────



    /**
     * Checks whether an email address is already registered.
     * Used during registration to enforce uniqueness before attempting an INSERT.
     *
     * @param email the email address to check
     * @return {@code true} if a user with this email already exists
     */
    boolean existsByEmail(String email);

    // ─────────────────────────────────────────────────────────────
    //  Collection queries
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of all users ordered by creation date descending.
     * Used by the admin listing endpoint.
     *
     * @param pageable pagination and sorting parameters
     * @return a page of users sorted newest-first
     */
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Returns all accounts that are currently locked and whose lock has not yet expired.
     * Used by the admin dashboard and the scheduled lock-expiry cleanup job.
     *
     * @param now the current UTC timestamp to compare against {@code locked_until}
     * @return list of users whose accounts are still within the lock window
     */
    @Query("SELECT u FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil > :now")
    List<User> findAllLockedUsers(@Param("now") LocalDateTime now);

    // ─────────────────────────────────────────────────────────────
    //  Write operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Increments the failed-login counter and sets the lock window for a user.
     *
     * <p>This is a bulk UPDATE — faster than loading the entity, modifying it,
     * and saving it back, especially under high authentication load.</p>
     *
     * @param userId      the ID of the user to update
     * @param attempts    the new failed-login attempt count
     * @param lockedUntil the UTC timestamp until which the account is locked
     *                    ({@code null} if the threshold has not yet been reached)
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = :attempts, u.lockedUntil = :lockedUntil WHERE u.id = :userId")
    void updateLockStatus(
            @Param("userId")      Long userId,
            @Param("attempts")    int attempts,
            @Param("lockedUntil") LocalDateTime lockedUntil
    );

    /**
     * Resets the failed-login counter and clears the lock for a user
     * after a successful authentication.
     *
     * @param userId the ID of the user to reset
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = NULL WHERE u.id = :userId")
    void resetFailedLoginAttempts(@Param("userId") Long userId);

    /**
     * Deactivates a user account without deleting the row (soft-delete).
     * The user will be unable to authenticate until re-activated.
     *
     * @param userId the ID of the user to deactivate
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.isActive = false WHERE u.id = :userId")
    void deactivateUser(@Param("userId") Long userId);

    @Query("SELECT u.id FROM User u WHERE u.identityFilePath = :identityFilePath")
    long findIdByIdentityFile(@Param("identityFilePath") String identityFilePath);
}
