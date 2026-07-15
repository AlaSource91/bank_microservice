package com.alaeldin.Auth_service.controller;

import com.alaeldin.Auth_service.dto.*;
import com.alaeldin.Auth_service.service.AdminService;
import com.alaeldin.Auth_service.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST controller for authentication operations.
 *
 * <p>Exposes the following endpoints under {@code /api/v1/auth}:</p>
 * <ul>
 *   <li>{@code POST /register}  — register a new user and receive a token pair</li>
 *   <li>{@code POST /login}     — authenticate and receive a token pair</li>
 *   <li>{@code POST /refresh}   — exchange a refresh token for a new token pair</li>
 *   <li>{@code POST /logout}    — invalidate the current access and refresh tokens</li>
 *   <li>{@code GET  /me}        — retrieve the authenticated user's profile</li>
 *   <li>{@code GET  /validate}  — lightweight token validation probe (used by the gateway)</li>
 * </ul>
 *
 * <p>All request bodies are validated via Bean Validation (JSR-380) — constraint
 * violations are handled globally by
 * {@link com.alaeldin.Auth_service.exception.GlobalExceptionHandler}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final AdminService adminService;

    // ─────────────────────────────────────────────────────────────
    //  Registration & login
    // ─────────────────────────────────────────────────────────────

    /**
     * Registers a new user account and returns a token pair.
     * 
     * <p>This endpoint accepts multipart/form-data with two parts:</p>
     * <ul>
     *   <li><strong>json</strong> — RegisterRequest data (Content-Type: application/json)</li>
     *   <li><strong>file</strong> — Identity document file (any Content-Type)</li>
     * </ul>
     *
     * @param request incoming HTTP request (captured for IP/audit logging)
     * @param registerRequest validated registration payload (must be sent as "json" part with Content-Type: application/json)
     * @param identityFileId identity document file upload (sent as "file" part)
     * @return {@code 201 Created} with an {@link AuthResponse} containing access + refresh tokens
     */
    @PostMapping(path = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> register(
            HttpServletRequest request,
            @Valid @RequestPart(value = "json", required = true) RegisterRequest registerRequest,
            @RequestPart(value = "file", required = true) MultipartFile identityFileId
    ) {
        try {
            log.info("[AuthController] POST /register email={}", registerRequest.getEmail());
            log.info("[AuthController] File received: name={}, size={} bytes, contentType={}",
                    identityFileId.getOriginalFilename(),
                    identityFileId.getSize(),
                    identityFileId.getContentType());
            
            AuthResponse authResponse = authService.register(registerRequest , request , identityFileId);
            log.info("[AuthController] Registration successful email={}, identityFilePath={}",
                    registerRequest.getEmail(),
                    authResponse.getIdentityFilePath());

            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);

        } catch (IOException e) {
            log.error("[AuthController] FILE UPLOAD FAILED for email={}", registerRequest.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "File upload failed: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.error("[AuthController] VALIDATION FAILED for email={}", registerRequest.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            log.error("[AuthController] REGISTRATION FAILED for email={}", registerRequest.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }

    }
    /**
     * Authenticates a user and returns a fresh token pair.
     *
     * @param loginRequest validated login payload
     * @param request      incoming HTTP request (captured for IP/audit logging)
     * @return {@code 200 OK} with an {@link AuthResponse} containing access + refresh tokens
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {

        log.info("[AuthController] POST /login Email={}", loginRequest.getEmail());
        AuthResponse authResponse = authService.login(loginRequest, request);
        log.info("[AuthController] Login successful Email={}", loginRequest.getEmail());

        return ResponseEntity.ok(authResponse);
    }

    // ─────────────────────────────────────────────────────────────
    //  Token lifecycle
    // ─────────────────────────────────────────────────────────────

    /**
     * Rotates the refresh token and returns a new token pair.
     *
     * @param refreshTokenRequest payload containing the current (valid) refresh token
     * @return {@code 200 OK} with a new {@link AuthResponse}
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {

        log.info("[AuthController] POST /refresh");
        AuthResponse authResponse = authService.refreshToken(refreshTokenRequest);
        log.info("[AuthController] Token refreshed successfully");

        return ResponseEntity.ok(authResponse);
    }

    /**
     * Logs the user out by blacklisting the access token and revoking all refresh tokens.
     *
     * @param request incoming HTTP request — must carry an {@code Authorization: Bearer <token>} header
     * @return {@code 200 OK} on success, {@code 400 Bad Request} if the header is absent or malformed
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[AuthController] Logout attempted without a valid Authorization header");
            return ResponseEntity.badRequest().body("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        authService.logout(token);
        log.info("[AuthController] Logout successful");

        return ResponseEntity.ok("Logged out successfully");
    }

    // ─────────────────────────────────────────────────────────────
    //  Profile & token validation
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param authentication Spring Security authentication object (injected by the framework)
     * @return {@code 200 OK} with a {@link UserProfileResponse}
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        log.info("[AuthController] GET /me username={}", authentication.getName());
        UserProfileResponse profile = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(profile);
    }


    @DeleteMapping("/")
    public ResponseEntity<String> delete(Authentication authentication) throws IOException {

        log.info("[AuthController] DELETE /me username={}", authentication.getName());
        boolean deletedFile = authService.deleteFileAndAccount();

       return ResponseEntity.ok("Deleted successfully");
    }

    /**
     * Lightweight endpoint used by the API gateway to verify a token is still valid.
     *
     * <p>Returns the username, granted authorities, and a server timestamp so that
     * the gateway can forward enriched headers downstream.</p>
     *
     * @param authentication Spring Security authentication object (injected by the framework)
     * @return {@code 200 OK} with a JSON map containing {@code valid}, {@code username},
     *         {@code authorities}, and {@code timestamp}
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(Authentication authentication) {
        log.debug("[AuthController] GET /validate username={}", authentication.getName());

        return ResponseEntity.ok(Map.of(
                "valid",       true,
                "username",    authentication.getName(),
                "authorities", authentication.getAuthorities().stream()
                               .map(GrantedAuthority::getAuthority)
                               .toList(),

                "timestamp",   LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(@RequestParam String fileName) throws IOException {

        byte[] image = authService.readFile(fileName);

        return ResponseEntity.ok()
                .header("Content-Type", "image/png")
                .body(image);
    }

    /**
     * Updates an existing user's information.
     *
     * <p>This endpoint supports partial updates — only the fields provided in the request
     * will be updated. The identity file is optional; if provided, it will replace the
     * existing file.</p>
     *
     * @param request the HTTP request for audit logging
     * @param file optional new identity document file
     * @return {@code 200 OK} with updated user information and new tokens
     * @throws ResponseStatusException {@code 400 Bad Request} if validation fails,
     *         {@code 404 Not Found} if user doesn't exist,
     *         {@code 409 Conflict} if email/phone/nationalId already exists,
     *         {@code 500 Internal Server Error} if file upload fails
     */
    @PutMapping(
            path = "/users",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AuthResponse> updateUser(
            HttpServletRequest request,

            @RequestPart("json") String json,

            @RequestPart(value = "file", required = false)
            MultipartFile file) {

        try {

            ObjectMapper mapper = new ObjectMapper();

            UpdateUserRequest updateUserRequest =
                    mapper.readValue(json, UpdateUserRequest.class);

            log.info("[AdminController] PUT /users userId={}",
                    updateUserRequest.getUserId());

            AuthResponse authResponse =
                    adminService.updateUser(updateUserRequest, request, file);

            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage(),
                    e
            );
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCurrentUserById(@PathVariable Long id) {
        return ResponseEntity.ok(authService.getCurrentUserById(id));
    }
}

