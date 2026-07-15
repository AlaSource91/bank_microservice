package com.alaeldin.Auth_service.repository;

import com.alaeldin.Auth_service.model.RefreshToken;
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
 * Repository for {@link RefreshToken} entities.
 *
 * <p>All write operations (bulk revoke / delete) are annotated with
 * {@link Transactional} so that Spring Data applies the right transaction
 * semantics even when called outside a pre-existing transaction boundary.</p>
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // -------------------------------------------------------------------------
    // Lookup by token hash (used on every silent-refresh request)
    // -------------------------------------------------------------------------

    /**
     * Finds a refresh token by its SHA-256 hex hash.
     *
     * <p>The {@code token_hash} column has a unique index, so this lookup is
     * O(log n) and will never return more than one row.</p>
     *
     * @param tokenHash the SHA-256 hex hash of the raw token
     * @return an {@link Optional} containing the token, or empty if not found
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // -------------------------------------------------------------------------
    // Lookup by user (used for session listing / audit)
    // -------------------------------------------------------------------------

    /**
     * Returns all active (non-revoked) tokens for the given user.
     *
     * <p>Used to list a user's live sessions or to count active tokens
     * before issuing a new one.</p>
     *
     * @param userId the primary key of the user
     * @return list of active tokens; empty list if none exist
     */
    List<RefreshToken> findByUser_IdAndIsRevokedFalse(Long userId);

    /**
     * Returns all tokens (active and revoked) for the given user.
     *
     * <p>Useful for audit / admin views.</p>
     *
     * @param userId the primary key of the user
     * @return list of all tokens belonging to the user
     */
    List<RefreshToken> findByUser_Id(Long userId);

    // -------------------------------------------------------------------------
    // Bulk revoke (used on logout / password-change)
    // -------------------------------------------------------------------------

    /**
     * Revokes all active tokens for the given user in a single UPDATE statement.
     *
     * <p>Preferred over loading each token individually because it avoids N+1
     * queries when a user has multiple active sessions.</p>
     *
     * @param userId the primary key of the user whose tokens should be revoked
     */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user.id = :userId AND rt.isRevoked = false")
    void revokeAllByUserId(@Param("userId") Long userId);

    // -------------------------------------------------------------------------
    // Bulk delete (used for account deletion / hard-logout)
    // -------------------------------------------------------------------------

    /**
     * Deletes all tokens (regardless of revocation state) for the given user.
     *
     * <p>Should be called when a user account is permanently deleted to avoid
     * leaving orphan rows in the {@code refresh_tokens} table.</p>
     *
     * @param userId the primary key of the user whose tokens should be deleted
     */
    @Transactional
    void deleteByUser_Id(Long userId);

    // -------------------------------------------------------------------------
    // Housekeeping (used by a scheduled cleanup job)
    // -------------------------------------------------------------------------

    /**
     * Deletes all tokens whose expiry date is before the given timestamp.
     *
     * <p>Should be called periodically (e.g. nightly) to prune expired tokens
     * and keep the table size bounded.</p>
     *
     * @param now the cutoff timestamp — tokens with {@code expiryDate < now} are deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    void deleteAllExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Checks whether a token hash already exists in the database.
     *
     * <p>Used as a fast pre-check during token issuance to detect (extremely
     * unlikely) hash collisions without loading the full entity.</p>
     *
     * @param tokenHash the SHA-256 hex hash to check
     * @return {@code true} if a token with this hash already exists
     */
    boolean existsByTokenHash(String tokenHash);
}
