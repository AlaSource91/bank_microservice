package com.alaeldin.Auth_service.service;

import com.alaeldin.Auth_service.model.Role;
import com.alaeldin.Auth_service.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Stateless service that issues, validates and revokes JSON Web Tokens.
 *
 * <p>Access tokens are short-lived signed JWTs that carry the user's identity,
 * primary role and flattened permissions list. Refresh tokens are opaque UUIDs
 * persisted in the database — only access tokens are handled here.</p>
 *
 * <p>Token revocation is implemented via a Redis blacklist keyed by the JWT ID
 * ({@code jti}). Every token carries a unique {@code jti}; on logout the
 * remaining TTL is stored in Redis so the blacklist entry auto-expires when
 * the token would have expired anyway.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    // ─────────────────────────────────────────────────────────────
    //  Configuration
    // ─────────────────────────────────────────────────────────────

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;


    private final RedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    // ─────────────────────────────────────────────────────────────
    //  Token generation
    // ─────────────────────────────────────────────────────────────

    /**
     * Generates a signed access JWT for the given user.
     *
     * <p>The token contains the user's subject (username), internal ID, primary role,
     * and the full flattened permissions list. A unique {@code jti} is embedded so the
     * token can be individually revoked via {@link #blacklistToken(String)}.</p>
     *
     * @param user the authentizcated user entity (roles and permissions must be initialised)
     * @return a compact, signed JWT string
     */
    public String generateAccessToken(User user) {
        Set<String> permissions = buildPermissionList(user);
        String primaryRole = user.getRoles().stream()
                .map(Role::getName)
                .findFirst()
                .orElse("USER");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())        // jti — required for blacklisting
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", primaryRole)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generates an opaque refresh-token value (UUID string).
     * The caller is responsible for persisting this value.
     *
     * @return a random UUID string
     */
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString();
    }

    // ─────────────────────────────────────────────────────────────
    //  Token validation & revocation
    // ─────────────────────────────────────────────────────────────

    /**
     * Validates the JWT signature and expiry, and checks the Redis blacklist.
     *
     * @param token raw JWT string (without {@code "Bearer "} prefix)
     * @return {@code true} if the token is structurally valid, not expired, and not blacklisted
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);

            // Expiry is already verified by the JJWT parser, but we do an
            // explicit check here to keep the log message informative.
            if (claims.getExpiration().before(new Date())) {
                log.debug("[JwtService] Token expired at {}", claims.getExpiration());
                return false;
            }

            // Check Redis blacklist — revoked tokens are stored by jti
            String jti = claims.getId();
            if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti))) {
                log.debug("[JwtService] Token is blacklisted jti={}", jti);
                return false;
            }

            return true;

        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("[JwtService] Invalid token: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Adds the token's {@code jti} to the Redis blacklist for its remaining lifetime.
     * Tokens without a {@code jti} (legacy tokens) are silently ignored.
     *
     * @param token raw JWT string to revoke
     */
    public void blacklistToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String jti = claims.getId();

            if (jti == null) {
                log.warn("[JwtService] Token has no jti — cannot blacklist");
                return;
            }

            long expiryMs = claims.getExpiration().getTime();
            long nowMs = Instant.now().toEpochMilli();
            long remainingSeconds = Math.max(0, (expiryMs - nowMs) / 1000);

            if (remainingSeconds > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + jti,
                        "revoked",
                        remainingSeconds,
                        TimeUnit.SECONDS
                );
                log.debug("[JwtService] Token blacklisted jti={} ttl={}s", jti, remainingSeconds);
            }

        } catch (Exception ex) {
            log.error("[JwtService] Failed to blacklist token: {}", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Claims extraction helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts the subject claim (username) from the token.
     *
     * @param token raw JWT string
     * @return the username stored in the {@code sub} claim
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the role claim from the token (e.g. {@code "ADMIN"}, {@code "USER"}).
     *
     * @param token raw JWT string
     * @return the role name, or {@code null} if absent
     */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Extracts the list of permission strings from the token
     * (e.g. {@code ["ACCOUNT:READ", "TRANSACTION:EXECUTE"]}).
     *
     * @param token raw JWT string
     * @return an immutable list of permission strings; never {@code null}
     */
    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        Object perms = parseClaims(token).get("permissions");
        if (perms instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    /**
     * Returns the expiration date of the token.
     *
     * @param token raw JWT string
     * @return the {@link Date} at which the token expires
     */
    public Date parseExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    /**
     * Extracts the JWT ID ({@code jti}) from the token.
     *
     * @param token raw JWT string
     * @return the jti claim value, or {@code null} if absent
     */
    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    // ─────────────────────────────────────────────────────────────
    //  Permission list builder
    // ─────────────────────────────────────────────────────────────

    /**
     * Flattens all permissions from every role the user holds into a sorted,
     * deduplicated list of {@code "RESOURCE:ACTION"} strings.
     *
     * @param user the user entity whose roles must be initialised
     * @return a sorted, distinct list of permission keys
     */
    public Set<String> buildPermissionList(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getResource().getName() + ":" + permission.getAction().name())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new)); // preserves sorted insertion order
    }

    // ─────────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Parses and verifies the JWT, returning its claims payload.
     * Throws a {@link JwtException} if the token is malformed, expired, or has an invalid signature.
     *
     * @param token raw JWT string
     * @return the verified {@link Claims}
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Decodes the Base64-encoded secret and builds the HMAC-SHA signing key.
     *
     * @return a {@link SecretKey} suitable for signing and verifying JWTs
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
