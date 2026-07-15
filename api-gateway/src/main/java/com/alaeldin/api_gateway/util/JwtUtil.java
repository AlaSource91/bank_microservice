package com.alaeldin.api_gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Utility class for JWT token operations including validation, parsing, and claim extraction.
 * This class provides thread-safe methods for working with JWT tokens in the API Gateway.
 *
 * @author Alaeldin
 * @version 1.1
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private SecretKey signingKey;

    /**
     * Initializes the signing key after properties are injected.
     * This caches the key to avoid recreating it on every call.
     */
    @PostConstruct
    private void init() {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("JWT secret must be configured");
        }
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT signing key initialized successfully");
    }

    /**
     * Returns the cached signing key used for JWT verification.
     *
     * @return SecretKey instance
     */
    private SecretKey getSigningKey() {
        return signingKey;
    }

    /**
     * Validates the JWT token by checking signature, expiration, and format.
     *
     * @param token JWT token string to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            log.warn("Token validation failed: Token is null or empty");
            return false;
        }

        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);

            log.debug("Token validated successfully");
            return true;

        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Token validation failed with unexpected error: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Extracts all claims from the JWT token.
     *
     * @param token JWT token string
     * @return Claims object containing all token claims
     * @throws JwtException if token is invalid or expired
     */
    public Claims extractAllClaims(String token) {
        validateTokenNotEmpty(token);

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Generic method to extract a specific claim from the token.
     *
     * @param token          JWT token string
     * @param claimsResolver function to extract the desired claim
     * @param <T>            type of the claim value
     * @return extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        validateTokenNotEmpty(token);

        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extracts the username (subject) from the token.
     *
     * @param token JWT token string
     * @return username from the token
     */
    public String extractUserName(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the user ID from the token.
     *
     * @param token JWT token string
     * @return user ID from the token, or null if not present
     */
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    /**
     * Extracts the user role from the token.
     *
     * @param token JWT token string
     * @return role from the token, or null if not present
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extracts the list of permissions from the token.
     *
     * @param token JWT token string
     * @return list of permissions, or empty list if not present
     */
    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        List<String> permissions = extractClaim(token, claims -> claims.get("permissions", List.class));
        return permissions != null ? permissions : Collections.emptyList();
    }

    /**
     * Extracts the JWT ID (jti claim) from the token.
     *
     * @param token JWT token string
     * @return JWT ID from the token
     */
    public String extractJwtId(String token) {
        return extractClaim(token, Claims::getId);
    }

    /**
     * Checks if the token has expired.
     *
     * @param token JWT token string
     * @return true if token is expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        if (!StringUtils.hasText(token)) {
            log.warn("Cannot check expiration: Token is null or empty");
            return true;
        }

        try {
            Date expiration = extractClaim(token, Claims::getExpiration);
            boolean expired = expiration.before(new Date());
            
            if (expired) {
                log.debug("Token has expired at: {}", expiration);
            }
            
            return expired;
            
        } catch (ExpiredJwtException e) {
            log.debug("Token is expired: {}", e.getMessage());
            return true;
        } catch (JwtException e) {
            log.warn("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Checks if the token contains the specified role (case-insensitive).
     *
     * @param token JWT token string
     * @param role  role to check for
     * @return true if token has the role, false otherwise
     */
    public boolean hasRole(String token, String role) {
        if (!StringUtils.hasText(role)) {
            log.warn("Role parameter is null or empty");
            return false;
        }

        try {
            String tokenRole = extractRole(token);
            return tokenRole != null && tokenRole.equalsIgnoreCase(role);
        } catch (Exception e) {
            log.error("Error checking role: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the token contains the specified permission.
     *
     * @param token      JWT token string
     * @param permission permission to check for
     * @return true if token has the permission, false otherwise
     */
    public boolean hasPermission(String token, String permission) {
        if (!StringUtils.hasText(permission)) {
            log.warn("Permission parameter is null or empty");
            return false;
        }

        try {
            List<String> permissions = extractPermissions(token);
            return permissions.contains(permission);
        } catch (Exception e) {
            log.error("Error checking permission: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the token belongs to an admin user.
     *
     * @param token JWT token string
     * @return true if token has ADMIN role, false otherwise
     */
    public boolean isAdmin(String token) {
        return hasRole(token, "ADMIN");
    }

    /**
     * Validates that a token is not null or empty.
     *
     * @param token token to validate
     * @throws IllegalArgumentException if token is null or empty
     */
    private void validateTokenNotEmpty(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("JWT token cannot be null or empty");
        }
    }

}
