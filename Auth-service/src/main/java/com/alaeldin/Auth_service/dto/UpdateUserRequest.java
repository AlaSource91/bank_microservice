package com.alaeldin.Auth_service.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

/**
 * Request payload for updating user information.
 *
 * <p>All fields except userId are optional. Only non-null fields will be updated.
 * This allows for partial updates where clients can send only the fields they want to change.</p>
 */
@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserRequest {

    /**
     * The ID of the user to update. This field is required.
     */
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be a positive number")
    private Long userId;

    /**
     * Updated first name (optional).
     */
    @Size(min = 3, max = 50, message = "First name must be between 3 to 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "First name must contain only letters, digits, and underscores")
    private String firstName;

    /**
     * Updated middle name (optional).
     */
    @Size(max = 50, message = "Middle name must not exceed 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "Middle name must contain only letters, digits, and underscores")
    private String middleName;

    /**
     * Updated last name (optional).
     */
    @Size(min = 3, max = 50, message = "Last name must be between 3 to 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "Last name must contain only letters, digits, and underscores")
    private String lastName;

    /**
     * Updated email (optional).
     */
    @Email(message = "Must be a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    /**
     * Updated phone number (optional).
     */
    @Pattern(regexp = "^\\+?[0-9]{10,14}$", message = "Phone number must be valid (10-14 digits, optional + prefix)")
    private String phone;

    /**
     * Updated national ID (optional).
     */
    @Size(max = 100, message = "National ID must not exceed 100 characters")
    private String nationalId;

    /**
     * Updated password (optional). Will be hashed before storage.
     */
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @JsonSetter
    public void setEmail(String email) {
        this.email = email != null ? email.trim() : null;
    }

    @JsonSetter("password")
    public void setPassword(String password) {
        this.password = password != null ? password.trim() : null;
    }
}

