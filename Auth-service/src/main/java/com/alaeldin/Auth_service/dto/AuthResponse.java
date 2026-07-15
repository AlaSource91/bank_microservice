package com.alaeldin.Auth_service.dto;

import com.alaeldin.Auth_service.model.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Response payload returned to the client after a successful authentication
 * (login or token refresh).
 *
 * <p>Follows the OAuth 2.0 token-response convention (RFC 6749 §5.1):</p>
 * <ul>
 *   <li>{@link #accessToken}  — short-lived JWT for authorising API calls.</li>
 *   <li>{@link #refreshToken} — long-lived opaque token for silent renewal.</li>
 *   <li>{@link #tokenType}    — always {@code "Bearer"}.</li>
 *   <li>{@link #expiresIn}    — access-token lifetime in <strong>seconds</strong>.</li>
 *   <li>{@link #issuedAt}     — UTC instant of issuance; lets clients compute the
 *                               absolute expiry without relying on their own clock.</li>
 * </ul>
 *
 * <p>This class is intentionally <strong>immutable</strong> — no setters are
 * exposed. The service layer constructs instances exclusively through the
 * Lombok {@code @Builder}. {@code @JsonInclude} suppresses {@code null}
 * fields so that optional claims (e.g. {@code roles} when empty) are not
 * serialized as {@code null} in the JSON body.</p>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    /**
     * Signed JWT access token.
     *
     * <p>Clients include this in the {@code Authorization: Bearer <token>} header
     * of every API request. Expires after {@link #expiresIn} seconds from
     * {@link #issuedAt}.</p>
     */
    private String accessToken;

    /**
     * Opaque refresh token used to obtain a new access token without
     * re-authenticating. Only the SHA-256 hash of this value is persisted
     * — see {@link com.alaeldin.Auth_service.model.RefreshToken}.
     *
     * <p><strong>Treat this as a secret.</strong> It should be stored in an
     * {@code HttpOnly} cookie, never in {@code localStorage}.</p>
     */
    private String refreshToken;

    /**
     * Token scheme — always {@code "Bearer"} per RFC 6750.
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Lifetime of the access token in <strong>seconds</strong>.
     *
     * <p>Clients use this together with {@link #issuedAt} to pre-emptively
     * refresh before the token expires:
     * {@code expiresAt = issuedAt.plusSeconds(expiresIn)}.</p>
     */
    private long expiresIn;

    /**
     * UTC instant at which this token pair was issued.
     *
     * <p>Allows the client to calculate the absolute expiry time reliably,
     * independent of any clock-skew between client and server.</p>
     */
    @Builder.Default
    private Instant issuedAt = Instant.now();

    /**
     * Internal numeric identifier of the authenticated user.
     * Useful for client-side caching and analytics — never use this as a
     * trust anchor; always validate the JWT signature on the server.
     */
    private Long userId;

    /**
     * Login handle of the authenticated user.
     * Matches {@link User#getFirstName()} ()} ()}.
     */
    private String firstName;
    /**
     *middle first Name  Authenticated User
     */
    private String middleName;
    /**
     * Last Name Of The  authenticated user
     */
    private String lastName;
    /**
     * Phone Number of The authenticated user
     */
    private String phone;
    /**
     * National Id of The authenticated user
     */
    private String nationalId;

    /**
     * Identity File Path of The authenticated user
     */
    private String identityFilePath;

    /**
     * Email address of the authenticated user.
     */
    private String email;

    /**
     * Set of role names granted to this user (e.g. {@code "ADMIN"}, {@code "USER"}).
     * Empty when the user holds no roles — never {@code null}.
     */
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    /**
     * Flat set of permission strings derived from the user's roles
     * (e.g. {@code "READ:ACCOUNT"}, {@code "WRITE:TRANSACTION"}).
     * Empty when no permissions are resolved — never {@code null}.
     */
    @Builder.Default
    private Set<String> permissions = new HashSet<>();
}


