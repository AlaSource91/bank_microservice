package com.alaeldin.Auth_service.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

/**
 * Request payload for the user login endpoint.
 *
 * <p>Validation constraints are aligned with the
 * {@link com.alaeldin.Auth_service.model.User} entity so that obviously
 * malformed credentials are rejected at the API boundary — before any
 * database lookup is performed.</p>
 *
 * <p>This class automatically trims whitespace from username and password
 * during deserialization to prevent authentication failures from accidental
 * leading or trailing spaces (a common user error when copy-pasting credentials).</p>
 */
@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    /**
     * The Email of the account attempting to log in.
     *
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Between 3 and 50 characters (matches {@code users.email} column length).</li>
     *   <li>Only letters, digits, and underscores — consistent with registration rules.</li>
     *   <li>Automatically trimmed during deserialization.</li>
     * </ul>
     */
    @NotBlank(message = "Email is required")
    @Size(min = 3, max = 50, message = "Email must be between 3 and 50 characters")
    @Email(message = "Invalid email format")

    private String email;

    /**
     * The plain-text password submitted by the user.
     *
     * <p><strong>Never log this value.</strong> The service layer compares it
     * against the stored hash using BCrypt / Argon2 and discards it immediately.</p>
     *
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>At least 8 characters — rejects trivially short inputs before any
     *       expensive hash comparison is attempted.</li>
     *   <li>Automatically trimmed during deserialization.</li>
     * </ul>
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    /**
     * Custom setter for email that trims whitespace.
     * This prevents authentication failures from accidental spaces.
     *
     * @param email the raw email from the JSON request
     */
    @JsonSetter("email")
    public void setEmail(String email) {
        this.email = email != null ? email.trim() : null;
    }

    /**
     * Custom setter for password that trims whitespace.
     * This prevents authentication failures from accidental spaces.
     *
     * @param password the raw password from the JSON request
     */
    @JsonSetter("password")
    public void setPassword(String password) {
        this.password = password != null ? password.trim() : null;
    }
}
