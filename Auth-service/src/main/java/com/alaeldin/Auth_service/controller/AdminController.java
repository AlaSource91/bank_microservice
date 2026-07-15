package com.alaeldin.Auth_service.controller;

import com.alaeldin.Auth_service.dto.*;
import com.alaeldin.Auth_service.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * REST controller exposing administrative operations for the P2P platform.
 *
 * <p>All endpoints are mounted under {@code /api/v1/admin}. Fine-grained
 * access control is enforced at the service layer via
 * {@code @PreAuthorize("hasAuthority('ADMIN_PANEL:...')")} — query endpoints
 * require {@code ADMIN_PANEL:READ} and mutation endpoints require
 * {@code ADMIN_PANEL:WRITE}.</p>
 *
 * <p>Available endpoints:</p>
 * <ul>
 *   <li>GET    /users                 — list all users (paginated)</li>
 *   <li>GET    /users/{id}            — get user by ID</li>
 *   <li>POST   /users                 — create a new user (JSON - no file)</li>
 *   <li>POST   /users/with-file       — create a new user (multipart - with file)</li>
 *   <li>PUT    /users                 — update an existing user (JSON - no file)</li>
 *   <li>PUT    /users/with-file       — update an existing user (multipart - with file)</li>
 *   <li>PATCH  /users/{id}/activate   — activate a user account</li>
 *   <li>PATCH  /users/{id}/deactivate — deactivate a user account</li>
 *   <li>POST   /roles/assign          — assign role to user</li>
 *   <li>DELETE /roles/revoke          — revoke role from user</li>
 *   <li>GET    /roles                 — list all roles (paginated)</li>
 *   <li>GET    /resources             — list all resources (paginated)</li>
 *   <li>GET    /permissions           — list all permissions (paginated)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Validated
public class AdminController {

    private final AdminService adminService;

    // ─────────────────────────────────────────────────────────────
    //  User queries
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of all registered users.
     *
     * @param pageable pagination and sort parameters (default: page 0, size 20)
     * @return {@code 200 OK} with a {@link Page} of {@link UserProfileResponse}
     */
    @GetMapping("/users")
    public ResponseEntity<Page<UserProfileResponse>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("[AdminController] GET /users page={} size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(adminService.getAllUsers(pageable));
    }

    /**
     * Returns the profile of a single user by their ID.
     *
     * @param id the user's primary key
     * @return {@code 200 OK} with the user's {@link UserProfileResponse}
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable Long id) {
        log.info("[AdminController] GET /users/{}", id);
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    // ─────────────────────────────────────────────────────────────
    //  User status mutations
    // ─────────────────────────────────────────────────────────────

    /**
     * Activates a user account so they can log in.
     *
     * @param id             the user's primary key
     * @param authentication the current admin principal (for audit logging)
     * @return {@code 204 No Content} on success
     */
    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<Void> activateUser(
            @PathVariable Long id,
            Authentication authentication) {

        log.info("[AdminController] PATCH /users/{}/activate by={}", id, authentication.getName());
        adminService.activateUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivates a user account, preventing future logins.
     *
     * @param id             the user's primary key
     * @param authentication the current admin principal (for audit logging)
     * @return {@code 204 No Content} on success
     */
    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable Long id,
            Authentication authentication) {

        log.info("[AdminController] PATCH /users/{}/deactivate by={}", id, authentication.getName());
        adminService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    //  Role assignment
    // ─────────────────────────────────────────────────────────────

    /**
     * Assigns a role to a user.
     *
     * @param request        validated payload containing {@code userId} and {@code roleName}
     * @param authentication the current admin principal (for audit logging)
     * @return {@code 200 OK} with the updated {@link UserProfileResponse}
     */
    @PostMapping("/roles/assign")
    public ResponseEntity<UserProfileResponse> assignRole(
            @Valid @RequestBody AssignRoleRequest request,
            Authentication authentication) {

        log.info("[AdminController] POST /roles/assign userId={} role={} by={}",
                request.getUserId(), request.getRoleName(), authentication.getName());
        return ResponseEntity.ok(adminService.assignRole(request, authentication.getName()));
    }

    /**
     * Revokes a role from a user.
     *
     * @param request        validated payload containing {@code userId} and {@code roleName}
     * @param authentication the current admin principal (for audit logging)
     * @return {@code 200 OK} with the updated {@link UserProfileResponse}
     */
    @DeleteMapping("/roles/revoke")
    public ResponseEntity<UserProfileResponse> revokeRole(
            @Valid @RequestBody AssignRoleRequest request,
            Authentication authentication) {

        log.info("[AdminController] DELETE /roles/revoke userId={} role={} by={}",
                request.getUserId(), request.getRoleName(), authentication.getName());
        return ResponseEntity.ok(adminService.revokeRole(request));
    }

    // ─────────────────────────────────────────────────────────────
    //  Role / Resource / Permission queries
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of all roles in the system.
     *
     * @param pageable pagination and sort parameters (default: page 0, size 20)
     * @return {@code 200 OK} with a {@link Page} of {@link RoleResponse}
     */
    @GetMapping("/roles")
    public ResponseEntity<Page<RoleResponse>> getAllRoles(
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("[AdminController] GET /roles page={} size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(adminService.getAllRoles(pageable));
    }

    /**
     * Returns a paginated list of all protected resources.
     *
     * @param pageable pagination and sort parameters (default: page 0, size 20)
     * @return {@code 200 OK} with a {@link Page} of {@link ResourceResponse}
     */
    @GetMapping("/resources")
    public ResponseEntity<Page<ResourceResponse>> getAllResources(
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("[AdminController] GET /resources page={} size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(adminService.getAllResources(pageable));
    }

    /**
     * Returns a paginated list of all permissions in the system.
     *
     * @param pageable pagination and sort parameters (default: page 0, size 20)
     * @return {@code 200 OK} with a {@link Page} of {@link PermissionResponse}
     */
    @GetMapping("/permissions")
    public ResponseEntity<Page<PermissionResponse>> getAllPermissions(
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("[AdminController] GET /permissions page={} size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(adminService.getAllPermissions(pageable));
    }


    // ─────────────────────────────────────────────────────────────
    //  User Creation & Update
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a new user account via admin panel (JSON only - no file upload).
     *
     * <p>This endpoint accepts a plain JSON request for creating users without
     * uploading an identity file. The new user is assigned the AUDITOR role by
     * default and receives authentication tokens immediately.</p>
     *
     * @param request the HTTP request for audit logging
     * @param addNewUserRequest validated payload containing new user details
     * @return {@code 201 Created} with new user information and tokens
     * @throws ResponseStatusException {@code 400 Bad Request} if validation fails,
     *         {@code 409 Conflict} if email/phone/nationalId already exists
     */
    @PostMapping(path = "/users",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> addNewUser(
            HttpServletRequest request,
            @Valid @RequestPart("json") AddNewUserRequest addNewUserRequest) {

        try {
            log.info("[AdminController] POST /users (JSON) email={}", addNewUserRequest.getEmail());

            AuthResponse authResponse = adminService.addNewUser(addNewUserRequest, request, null);

            log.info("[AdminController] User created successfully (no file): email={}, userId={}",
                    addNewUserRequest.getEmail(), authResponse.getUserId());

            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);

        } catch (IOException e) {
            log.error("[AdminController] Unexpected IO error for email={}",
                    addNewUserRequest.getEmail(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error: " + e.getMessage(),
                    e);
        } catch (IllegalArgumentException e) {
            log.error("[AdminController] Validation failed for email={}",
                    addNewUserRequest.getEmail(), e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Validation failed: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Creates a new user account via admin panel (with file upload).
     *
     * <p>This endpoint accepts a multipart request for creating users WITH
     * an identity file upload. The new user is assigned the AUDITOR role by
     * default and receives authentication tokens immediately.</p>
     *
     * @param request the HTTP request for audit logging
     * @param addNewUserRequest validated payload containing new user details
     * @param identityFile identity document file (e.g., national ID, passport)
     * @return {@code 201 Created} with new user information and tokens
     * @throws ResponseStatusException {@code 400 Bad Request} if validation fails,
     *         {@code 409 Conflict} if email/phone/nationalId already exists,
     *         {@code 500 Internal Server Error} if file upload fails
     */
    @PostMapping(path = "/users/with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> addNewUserWithFile(
            HttpServletRequest request,
            @Valid @RequestPart("json") AddNewUserRequest addNewUserRequest,
            @RequestPart(value = "file", required = false) MultipartFile identityFile) {

        try {
            log.info("[AdminController] POST /users/with-file email={}", addNewUserRequest.getEmail());

            if (identityFile != null && !identityFile.isEmpty()) {
                log.info("[AdminController] File received: name={}, size={} bytes, contentType={}",
                        identityFile.getOriginalFilename(),
                        identityFile.getSize(),
                        identityFile.getContentType());
            } else {
                log.info("[AdminController] No identity file provided");
            }

            AuthResponse authResponse = adminService.addNewUser(addNewUserRequest, request, identityFile);

            log.info("[AdminController] User created successfully: email={}, userId={}",
                    addNewUserRequest.getEmail(), authResponse.getUserId());

            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);

        } catch (IOException e) {
            log.error("[AdminController] File upload failed for email={}",
                    addNewUserRequest.getEmail(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "File upload failed: " + e.getMessage(),
                    e);
        } catch (IllegalArgumentException e) {
            log.error("[AdminController] Validation failed for email={}",
                    addNewUserRequest.getEmail(), e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Validation failed: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Updates an existing user's information (JSON only - no file upload).
     *
     * <p>This endpoint accepts a plain JSON request for updating users without
     * uploading a new identity file. Only the fields provided in the request
     * will be updated (partial update).</p>
     *
     * @param request the HTTP request for audit logging
     * @param updateUserRequest validated payload with userId and fields to update
     * @return {@code 200 OK} with updated user information and new tokens
     * @throws ResponseStatusException {@code 400 Bad Request} if validation fails,
     *         {@code 404 Not Found} if user doesn't exist,
     *         {@code 409 Conflict} if email/phone/nationalId already exists
     */
    @PutMapping(path = "/users", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> updateUser(
            HttpServletRequest request,
            @Valid @RequestBody UpdateUserRequest updateUserRequest) {

        try {
            log.info("[AdminController] PUT /users (JSON) userId={}", updateUserRequest.getUserId());

            AuthResponse authResponse = adminService.updateUser(updateUserRequest, request, null);

            log.info("[AdminController] User updated successfully (no file): userId={}",
                    updateUserRequest.getUserId());
            return ResponseEntity.ok(authResponse);

        } catch (IOException e) {
            log.error("[AdminController] Unexpected IO error for userId={}",
                    updateUserRequest.getUserId(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error: " + e.getMessage(),
                    e);
        } catch (IllegalArgumentException e) {
            log.error("[AdminController] Validation failed for userId={}",
                    updateUserRequest.getUserId(), e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Validation failed: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Updates an existing user's information (with file upload).
     *
     * <p>This endpoint accepts a multipart request for updating users WITH
     * a new identity file upload. Only the fields provided will be updated.</p>
     *
     * @param request the HTTP request for audit logging
     * @param updateUserRequest validated payload with userId and fields to update
     * @param file optional new identity document file
     * @return {@code 200 OK} with updated user information and new tokens
     * @throws ResponseStatusException {@code 400 Bad Request} if validation fails,
     *         {@code 404 Not Found} if user doesn't exist,
     *         {@code 409 Conflict} if email/phone/nationalId already exists,
     *         {@code 500 Internal Server Error} if file upload fails
     */
    @PutMapping(path = "/users/with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> updateUserWithFile(
            HttpServletRequest request,
            @Valid @RequestPart("json") UpdateUserRequest updateUserRequest,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        try {
            log.info("[AdminController] PUT /users/with-file userId={}", updateUserRequest.getUserId());

            if (file != null && !file.isEmpty()) {
                log.info("[AdminController] File received: name={}, size={} bytes, contentType={}",
                        file.getOriginalFilename(), file.getSize(), file.getContentType());
            } else {
                log.info("[AdminController] No file provided for update");
            }

            AuthResponse authResponse = adminService.updateUser(updateUserRequest, request, file);

            log.info("[AdminController] User updated successfully: userId={}", updateUserRequest.getUserId());
            return ResponseEntity.ok(authResponse);

        } catch (IOException e) {
            log.error("[AdminController] File upload failed for userId={}",
                    updateUserRequest.getUserId(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "File upload failed: " + e.getMessage(),
                    e);
        } catch (IllegalArgumentException e) {
            log.error("[AdminController] Validation failed for userId={}",
                    updateUserRequest.getUserId(), e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Validation failed: " + e.getMessage(),
                    e);
        }
    }
}

